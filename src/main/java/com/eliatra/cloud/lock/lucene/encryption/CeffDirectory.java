/* 
 * Copyright (C) 2021 by eliatra Ltd. - All Rights Reserved
 * Unauthorized copying, usage or modification of this file in its source or binary form, 
 * via any medium is strictly prohibited.
 * Proprietary and confidential.
 * 
 * https://eliatra.com
 */
package com.eliatra.cloud.lock.lucene.encryption;

import com.eliatra.cloud.lock.crypto.EncryptedSymmetricKey;
import com.eliatra.cloud.lock.crypto.SymmetricKek;
import com.eliatra.cloud.lock.crypto.TemporarySymmetricKey;
import org.apache.lucene.index.IndexFileNames;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.FSLockFactory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.store.LockFactory;
import org.apache.lucene.store.MMapDirectory;
import org.apache.lucene.store.NIOFSDirectory;
import org.apache.lucene.util.Constants;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.util.function.Supplier;

/**
 * A Lucene {@link FSDirectory} implementations which wraps another FSDirectory and encrypt and
 * decrypt all read and write requests with a symmetric AEAD encryption scheme (see {@link
 * CeffMode}).
 */
public final class CeffDirectory extends FSDirectory {


  private static final String CEFF_KEY_FILE_NAME = "_encrypted_ceff_shard_key";

  public static final int DEFAULT_CHUNK_LENGTH = 64 * 1024; // 64kb

  private final FSDirectory delegate;
  private final int chunkLength;

  private final byte[] shardKey;
  private final CeffMode mode;
  private final boolean failOnPlaintext;

  /**
   * Create a new encrypted directory. Uses a chunks length of 64kb.
   *
   * @param delegate The wrapped implementation, typically {@link MMapDirectory} or {@link
   *     NIOFSDirectory}
   * @param kekSupplier KeyPair supplier to encrypt/decrypt the generated symmetric directory key
   * @throws IOException if the delegate throws an IOException or if there were issues with
   *     en-/decryption
   */
  public CeffDirectory(FSDirectory delegate, Supplier<SymmetricKek> kekSupplier, boolean failOnPlaintext) throws IOException {
    this(
        delegate,
        FSLockFactory.getDefault(),
            kekSupplier,
        DEFAULT_CHUNK_LENGTH,
        Constants.JRE_IS_MINIMUM_JAVA11 ? CeffMode.CHACHA20_POLY1305_MODE : CeffMode.AES_GCM_MODE, failOnPlaintext);
  }

  /**
   * Create a new encrypted directory.
   *
   * @param delegate The wrapped implementation, typically {@link MMapDirectory} or {@link
   *     NIOFSDirectory}
   * @param kekSupplier KeyPair supplier to encrypt/decrypt the generated symmetric directory key
   * @param chunkLength The length (size) of a chunk in bytes. See {@link CeffMode}
   * @param mode See {@link CeffMode}
   * @throws IOException if the delegate throws an IOException or if there were issues with
   *     en-/decryption
   */
  public CeffDirectory(FSDirectory delegate, Supplier<SymmetricKek> kekSupplier, int chunkLength, CeffMode mode, boolean failOnPlaintext)
      throws IOException {
    this(delegate, FSLockFactory.getDefault(), kekSupplier, chunkLength, mode, failOnPlaintext);
  }

  /**
   * Create a new encrypted directory.
   *
   * @param delegate The wrapped implementation, typically {@link MMapDirectory} or {@link
   *     NIOFSDirectory}
   * @param lockFactory A {@link LockFactory}
   * @param kekSupplier KeyPair supplier to encrypt/decrypt the generated symmetric directory key
   * @param chunkLength The length (size) of a chunk in bytes. See {@link CeffMode}
   * @param mode See {@link CeffMode}
   * @throws IOException if the delegate throws an IOException or if there were issues with
   *     en-/decryption
   */
  public CeffDirectory(
      FSDirectory delegate, LockFactory lockFactory, Supplier<SymmetricKek> kekSupplier, int chunkLength, CeffMode mode, boolean failOnPlaintext)
          throws IOException {
    super(delegate.getDirectory(), lockFactory);
    this.delegate = delegate;
    this.mode = mode;
    this.failOnPlaintext = failOnPlaintext;
    //this.mode.validateKey(key);
    this.chunkLength = chunkLength;
    CeffUtils.validateChunkLength(this.chunkLength);

    if(delegate instanceof CeffDirectory) {
      throw new RuntimeException("delegate is ceff");
    }

    byte[] _key;

    try {
      if(Files.exists(getDirectory().getParent().resolve(CEFF_KEY_FILE_NAME))) {
        _key = decryptDirectoryKey(kekSupplier.get());
      } else {
        _key = createNewDirectoryKey(kekSupplier.get());
      }
    } catch (Exception e) {
      if(e instanceof GeneralSecurityException) {
        throw new IOException(new CeffCryptoException("Unable to decrypt key",e,mode));
      }
      throw new IOException(e);
    }
    shardKey = _key;
  }

  private byte[] createNewDirectoryKey(SymmetricKek symmetricKek) throws Exception {
    final TemporarySymmetricKey temporarySymmetricKey = symmetricKek.newEncryptedSymmetricKey(SymmetricKek.KeyType.PLAIN_AES);
    Files.write(getDirectory().getParent().resolve(CEFF_KEY_FILE_NAME), temporarySymmetricKey.getEncryptedKey()
            , StandardOpenOption.CREATE_NEW
            , StandardOpenOption.WRITE
            , StandardOpenOption.SYNC
            , StandardOpenOption.DSYNC

    );
    return temporarySymmetricKey.getPlainKey();
  }

  private byte[] decryptDirectoryKey(SymmetricKek symmetricKek) throws Exception {
    final byte[] encryptedKey =  Files.readAllBytes(getDirectory().getParent().resolve(CEFF_KEY_FILE_NAME));
    EncryptedSymmetricKey encryptedSymmetricKey = EncryptedSymmetricKey.fromRawBytes(encryptedKey);
    assert encryptedSymmetricKey.getKeyType() == SymmetricKek.KeyType.PLAIN_AES;
    TemporarySymmetricKey temporarySymmetricKey = symmetricKek.decryptKey(encryptedSymmetricKey);
    assert temporarySymmetricKey.getKeyType() == SymmetricKek.KeyType.PLAIN_AES;
    return temporarySymmetricKey.getPlainKey();
  }


  @Override
  public IndexInput openInput(String fileName, IOContext context) throws IOException {
    this.ensureOpen();
    this.ensureCanRead(fileName);
    final IndexInput tmpInput = this.delegate.openInput(fileName, context);

    if (tmpInput.length() == 0) {
      return tmpInput;
    }

    if(isUnencrypted(fileName)){
      return tmpInput;
    }

    if(!failOnPlaintext) {

      if (tmpInput.length() < CeffUtils.headerLength(mode)) {
        return tmpInput;
      }

      // read plaintext files
      if (tmpInput.readInt() != CeffUtils.CEFF_MAGIC) {
        tmpInput.seek(0);
        return tmpInput;
      } else {
        tmpInput.seek(0);
      }
    }

    try {
      return new CeffIndexInput(tmpInput, this.shardKey);
    } catch (final IOException e) {
      tmpInput.close();
      throw e;
    }
  }

  @Override
  public IndexOutput createOutput(String fileName, IOContext context) throws IOException {
    final IndexOutput tmpOutput = this.delegate.createOutput(fileName, context);
    try {
      if(isUnencrypted(fileName)){
        return tmpOutput;
      }
      return new CeffIndexOutput(tmpOutput, this.chunkLength, this.shardKey, this.mode);
    } catch (final IOException e) {
      tmpOutput.close();
      throw e;
    } catch (CeffCryptoException e) {
      tmpOutput.close();
      throw new IOException(e);
    }
  }

  @Override
  public IndexOutput createTempOutput(String prefix, String suffix, IOContext context)
      throws IOException {
    final IndexOutput tmpOutput = this.delegate.createTempOutput(prefix, suffix, context);
    try {
      return new CeffIndexOutput(tmpOutput, this.chunkLength, this.shardKey, this.mode);
    } catch (final IOException e) {
      tmpOutput.close();
      throw e;
    } catch (CeffCryptoException e) {
      tmpOutput.close();
      throw new IOException(e);
    }
  }

  @Override
  public synchronized void close() throws IOException {
    this.delegate.close();
    super.close();
  }

  public int getChunkLength() {
    return this.chunkLength;
  }

  public CeffMode getMode() {
    return this.mode;
  }

  public FSDirectory getDelegate() {
    return this.delegate;
  }

  private boolean isUnencrypted(String fileName) {
    //recovery.gzYQEWzvShazqZ1GrGVtSA.segments_2
    return fileName.endsWith(".si")
            || fileName.contains(IndexFileNames.PENDING_SEGMENTS+"_")
            || fileName.contains(IndexFileNames.SEGMENTS+"_");
  }
}

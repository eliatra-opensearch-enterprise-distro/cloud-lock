/* 
 * Copyright (C) 2021 by eliatra Ltd. - All Rights Reserved
 * Unauthorized copying, usage or modification of this file in its source or binary form, 
 * via any medium is strictly prohibited.
 * Proprietary and confidential.
 * 
 * https://eliatra.com
 */

package com.eliatra.cloud.lock.plugin;

import com.carrotsearch.randomizedtesting.RandomizedTest;
import com.eliatra.cloud.lock.crypto.PlainSymmetricAeadAesKey;
import com.eliatra.cloud.lock.crypto.SymmetricKek;
import com.eliatra.cloud.lock.lucene.encryption.CeffDirectory;
import com.eliatra.cloud.lock.lucene.encryption.CeffMode;
import com.eliatra.cloud.lock.lucene.encryption.CeffUtils;
import com.google.crypto.tink.KeyTemplates;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.config.TinkConfig;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.store.NIOFSDirectory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.store.BaseDirectoryTestCase;
import org.apache.lucene.tests.util.English;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;

public class CeffDirectoryTests extends BaseDirectoryTestCase {

  protected static final SymmetricKek DEFAULT_KEY;

  static {

    try {
      TinkConfig.register();
      DEFAULT_KEY = new SymmetricKek(new PlainSymmetricAeadAesKey(KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM"))), new byte[0]);
    } catch (GeneralSecurityException e) {
      throw new RuntimeException(e);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    try {
      OTHER_KEY = new SymmetricKek(new PlainSymmetricAeadAesKey(KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM"))), new byte[0]);
    } catch (GeneralSecurityException e) {
      throw new RuntimeException(e);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected static final SymmetricKek OTHER_KEY;

  protected final SymmetricKek key;


  public CeffDirectoryTests() {
    this.key = DEFAULT_KEY;
  }

  @Override
  protected Directory getDirectory(Path path) throws IOException {
    return new CeffDirectory(
        new NIOFSDirectory(path),

            ()->this.key, true);
  }

  protected Directory getDirectoryOtherKey(Path path) throws IOException {
    return new CeffDirectory(
        new NIOFSDirectory(path),
            () -> OTHER_KEY, true);
  }

  @Test
  public void testBuildIndex() throws IOException {
    try (Directory dir = this.getDirectory(createTempDir("testBuildIndex"));
        IndexWriter writer =
            new IndexWriter(
                dir,
                new IndexWriterConfig(new MockAnalyzer(random())).setOpenMode(OpenMode.CREATE))) {
      final int docs = RandomizedTest.randomIntBetween(0, 10);
      for (int i = docs; i > 0; i--) {
        final Document doc = new Document();
        doc.add(newStringField("content", English.intToEnglish(i).trim(), Field.Store.YES));
        writer.addDocument(doc);
      }
      writer.commit();
      assertEquals(docs, writer.getDocStats().numDocs);
    }
  }

  // file is not directly comparable because we add some extra bytes
  @Override
  public void testCopyBytes() throws Exception {
    try (Directory dir = this.getDirectory(createTempDir("testCopyBytes"))) {
      IndexOutput out = dir.createOutput("test", newIOContext(random()));
      final byte[] bytes = new byte[TestUtil.nextInt(random(), 1, 77777)];
      final int size = TestUtil.nextInt(random(), 1, 1777777);
      int upto = 0;
      int byteUpto = 0;
      while (upto < size) {
        bytes[byteUpto++] = value(upto);
        upto++;
        if (byteUpto == bytes.length) {
          out.writeBytes(bytes, 0, bytes.length);
          byteUpto = 0;
        }
      }

      out.writeBytes(bytes, 0, byteUpto);
      assertEquals(size, out.getFilePointer());
      out.close();
      final long encryptedFileLength = dir.fileLength("test");
      final long calculatedFileLength = CeffUtils.calculateEncryptionOverhead(encryptedFileLength, CeffDirectory.DEFAULT_CHUNK_LENGTH, CeffMode.CHACHA20_POLY1305_MODE) + size;
      assertEquals("Difference: "+Math.abs(encryptedFileLength-calculatedFileLength)+" bytes",
             calculatedFileLength,
          encryptedFileLength);

      // copy from test -> test2
      final IndexInput in = dir.openInput("test", newIOContext(random()));

      out = dir.createOutput("test2", newIOContext(random()));

      upto = 0;
      while (upto < size) {
        if (random().nextBoolean()) {
          out.writeByte(in.readByte());
          upto++;
        } else {
          final int chunk = Math.min(TestUtil.nextInt(random(), 1, bytes.length), size - upto);
          out.copyBytes(in, chunk);
          upto += chunk;
        }
      }
      assertEquals(size, upto);
      out.close();
      in.close();

      // verify
      final IndexInput in2 = dir.openInput("test2", newIOContext(random()));
      upto = 0;
      while (upto < size) {
        if (random().nextBoolean()) {
          final byte v = in2.readByte();
          assertEquals(value(upto), v);
          upto++;
        } else {
          final int limit = Math.min(TestUtil.nextInt(random(), 1, bytes.length), size - upto);
          in2.readBytes(bytes, 0, limit);
          for (int byteIdx = 0; byteIdx < limit; byteIdx++) {
            assertEquals(value(upto), bytes[byteIdx]);
            upto++;
          }
        }
      }
      in2.close();

      dir.deleteFile("test");
      dir.deleteFile("test2");
    }
  }

  public void testWriteRead() throws Exception {
    final StandardAnalyzer analyzer = new StandardAnalyzer();

    final int segments = random().nextInt(4) + 1; // 101
    final int docsPerSegment = random().nextInt(44) + 1; // 10001
    final boolean compoundFile = random().nextBoolean();

    try (Directory dir = this.getDirectory(createTempDir("testWriteRead"))) {

      for (int s = 0; s < segments; s++) {

        final IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setUseCompoundFile(compoundFile);

        try (IndexWriter w = new IndexWriter(dir, config)) {

          for (int d = 0; d < docsPerSegment; d++) {
            final Document doc = new Document();
            doc.add(
                new TextField(
                    "title", "my fantastic book title " + d + " in seg " + s, Field.Store.YES));
            doc.add(
                new StringField(
                    "comments", "my hilarious comments " + d + " in seg " + s, Field.Store.YES));
            doc.add(
                new TextField("author", "Max Kai Musterman " + d + " in seg " + s, Field.Store.NO));
            doc.add(
                new StringField(
                    "publisher", "the glorious publisher " + d + " in seg " + s, Field.Store.YES));

            for (int k = 0; k < 3; k++) {
              doc.add(new StringField("testfield " + k, "value of " + k, Field.Store.YES));
              doc.add(new IntPoint("testfield int " + k, k));
              doc.add(new StoredField("testfield sf " + k, k));
              doc.add(new NumericDocValuesField("testfield ndv " + k, k));
              doc.add(new NumericDocValuesField("testfield ndv " + k + "-" + d, k));
              doc.add(new StringField("testsdv " + k, "12345", Field.Store.YES));
              doc.add(new SortedDocValuesField("testsdv " + k, new BytesRef("12345")));
            }

            final String id = s + "_" + d;
            doc.add(new StringField("id", id, Field.Store.NO));
            w.addDocument(doc);
          }
        }
      }

      final int maxHits = docsPerSegment * segments;
      try (IndexReader reader = DirectoryReader.open(dir)) {

        Assert.assertEquals(segments * docsPerSegment, reader.numDocs());
        Assert.assertEquals(0, reader.numDeletedDocs());
        Assert.assertEquals(segments * docsPerSegment, reader.maxDoc());

        int numDocsFromLeaves = 0;

        for (final LeafReaderContext lrc : reader.leaves()) {
          numDocsFromLeaves += lrc.reader().numDocs();
          Assert.assertNull(lrc.reader().getLiveDocs());
        }

        Assert.assertEquals(reader.numDocs(), numDocsFromLeaves);

        final Query query = new MatchAllDocsQuery();
        final IndexSearcher searcher = new IndexSearcher(reader);
        final TopScoreDocCollector collector = TopScoreDocCollector.create(maxHits, maxHits);
        searcher.search(query, collector);
        final ScoreDoc[] hits = collector.topDocs().scoreDocs;

        Assert.assertEquals(docsPerSegment * segments, hits.length);

        for (int i = 0; i < hits.length; ++i) {
          final int docId = hits[i].doc;
          final Document d = searcher.doc(docId);
          Assert.assertTrue(d.get("title").contains("book"));
          Assert.assertTrue(d.get("publisher").contains("glorious"));
        }

        final IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setUseCompoundFile(compoundFile);
        try (IndexWriter w = new IndexWriter(dir, config)) {
          w.forceMerge(1, true);
        }
      }
    }
  }

  public void testMinimalData() throws Exception {
    final StandardAnalyzer analyzer = new StandardAnalyzer();
    final Path tmpDirPath = createTempDir("testMinimalData");

    try (CeffDirectory dir = (CeffDirectory) this.getDirectory(tmpDirPath)) {

      final IndexWriterConfig config = new IndexWriterConfig(analyzer);

      try (IndexWriter w = new IndexWriter(dir, config)) {
        final Document doc = new Document();
        w.addDocument(doc);
      }

      try (IndexReader reader = DirectoryReader.open(dir)) {
        final Query query = new MatchAllDocsQuery();
        final IndexSearcher searcher = new IndexSearcher(reader);
        final TopScoreDocCollector collector = TopScoreDocCollector.create(100, 100);
        searcher.search(query, collector);
        final ScoreDoc[] hits = collector.topDocs().scoreDocs;
        Assert.assertEquals(1, hits.length);
      }
    }
  }

  public void testTamperedWith() throws Exception {
    final StandardAnalyzer analyzer = new StandardAnalyzer();

    final Path tmpDirPath = createTempDir("testWriteRead");

    try (CeffDirectory dir = (CeffDirectory) this.getDirectory(tmpDirPath)) {

      final IndexWriterConfig config = new IndexWriterConfig(analyzer);

      try (IndexWriter w = new IndexWriter(dir, config)) {

        final Document doc = new Document();
        doc.add(new TextField("title", "my fantastic book title ", Field.Store.YES));
        doc.add(new StringField("comments", "my hilarious comments ", Field.Store.YES));
        doc.add(new TextField("author", "Max Kai Musterman ", Field.Store.NO));
        doc.add(new StringField("publisher", "the glorious publisher ", Field.Store.YES));

        for (int k = 0; k < 5; k++) {
          doc.add(new StringField("testfield " + k, "value of " + k, Field.Store.YES));
          doc.add(new IntPoint("testfield int " + k, k));
          doc.add(new StoredField("testfield sf " + k, k));
          doc.add(new NumericDocValuesField("testfield ndv " + k, k));
          doc.add(new StringField("testsdv " + k, "12345", Field.Store.YES));
          doc.add(new SortedDocValuesField("testsdv " + k, new BytesRef("12345")));
        }

        w.addDocument(doc);
      }
    }

    expectThrows(
            IOException.class,
        () -> {
          try (CeffDirectory dir = (CeffDirectory) this.getDirectory(tmpDirPath)) {
            DirectoryReader.open(dir.getDelegate());
          }
        });

    // wrong key
    expectThrows(
            Exception.class,
            () -> {
              try (CeffDirectory dir = (CeffDirectory) this.getDirectoryOtherKey(tmpDirPath)) {

              }
            });

    final Path file;
    try (CeffDirectory dir = (CeffDirectory) this.getDirectory(tmpDirPath)) {
      file = tmpDirPath.resolve(dir.listAll()[0]);
      this.prepareFile(file);

      try (FileChannel fc =
                   FileChannel.open(file, StandardOpenOption.WRITE, StandardOpenOption.SYNC, StandardOpenOption.DSYNC)) {
        fc.position(fc.size()-17);
        final byte[] rand = new byte[3];
        random().nextBytes(rand);
        fc.write(ByteBuffer.wrap(rand));
      }

      expectThrows(
              IOException.class,
              () -> {
                DirectoryReader.open(dir);
              });

      this.restoreFile(file);
    }

    try (CeffDirectory dir = (CeffDirectory) this.getDirectory(tmpDirPath)) {
      this.prepareFile(file);

      try (FileChannel fc =
                   FileChannel.open(file, StandardOpenOption.WRITE, StandardOpenOption.SYNC)) {
        fc.position(41);
        final byte[] rand = new byte[3];
        random().nextBytes(rand);
        fc.write(ByteBuffer.wrap(rand));
      }

      expectThrows(
              IOException.class,
              () -> {
                DirectoryReader.open(dir);
              });

      this.restoreFile(file);
    }
  }

  private void prepareFile(Path file) throws IOException {
    Files.copy(
        file,
        file.getParent().resolve(file.getFileName() + ".enctmp"),
        StandardCopyOption.REPLACE_EXISTING);
  }

  private void restoreFile(Path file) throws IOException {
    Files.move(
        file.getParent().resolve(file.getFileName() + ".enctmp"),
        file.getParent().resolve(file.getFileName().toString().replace(".enctmp", "")),
        StandardCopyOption.REPLACE_EXISTING);
  }

  private static byte value(int idx) {
    return (byte) ((idx % 256) * (1 + (idx / 256)));
  }
}

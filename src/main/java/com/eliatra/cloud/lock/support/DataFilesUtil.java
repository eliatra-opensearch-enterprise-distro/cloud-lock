/*
 * Copyright 2024 by Eliatra - All rights reserved
 *
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed here is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
 * This software is free of charge for non-commercial and academic use.
 * For commercial use in a production environment you have to obtain a license
 * from https://eliatra.com
 *
 */

package com.eliatra.cloud.lock.support;

import org.opensearch.env.Environment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class DataFilesUtil {

    public static Path findLocation(Environment environment, String filename) {
        return findLocation(environment, Paths.get(filename ));
    }
    public static Path findLocation(Environment environment, Path filepath) {
        for(Path path: environment.dataFiles()) {
            if(Files.exists(path.resolve(filepath))) {
                return path;
            }
        }

        return environment.dataFiles()[0];
    }

}

/**
 * Anserini: An information retrieval toolkit built on Lucene
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.anserini.collection;

import edu.unh.cs.treccar_v2.Data;
import edu.unh.cs.treccar_v2.Header.InvalidHeaderException;
import edu.unh.cs.treccar_v2.Header.TrecCarHeader;
import edu.unh.cs.treccar_v2.read_data.DeserializeData;

import io.anserini.document.CAR18Document;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.wikiclean.WikiClean;
import org.wikiclean.WikiClean.WikiLanguage;
import org.wikiclean.WikiCleanBuilder;
import org.wikiclean.WikipediaBz2DumpInputStream;

/**
 * Class representing an instance of a Wikipedia collection. Note that Wikipedia dumps often come
 * as a single bz2 file. Since a collection is assumed to be in a directory, place the bz2 file in
 * a directory prior to indexing.
 */
public class CAR18Collection extends Collection<CAR18Document> {

  public class FileSegment extends Collection.FileSegment {
//    private final WikipediaBz2DumpInputStream stream;
//    private final WikiClean cleaner;
      private final FileInputStream stream;
      private final String mode;

    protected FileSegment(Path path) throws IOException {
      this.path = path;
      stream = new FileInputStream(new File(path.toString()));
    }

    @Override
    public CAR18Document next() {
      try {
        while (true) {
          if (mode.equals("header")) {
            TrecCarHeader contents = DeserializeData.getTrecCarHeader(stream);
          }
          if (contents.equals(""))
            break;
          System.out.println(contents);
        }
      } catch (InvalidHeaderException e) {
        e.printStackTrace();
      }

      // If we've fall through here, we've either encountered an exception or we've reached the end
      // of the underlying stream.
      atEOF = true;
      return null;
    }
  }

  @Override
  public List<Path> getFileSegmentPaths() {
    Set<String> allowedFileSuffix = new HashSet<>(Arrays.asList(".bz2"));

    return discover(path, EMPTY_SET, EMPTY_SET, EMPTY_SET,
        allowedFileSuffix, EMPTY_SET);
  }

  @Override
  public FileSegment createFileSegment(Path p) throws IOException {
    return new FileSegment(p);
  }

}

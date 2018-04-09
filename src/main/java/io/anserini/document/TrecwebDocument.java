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

package io.anserini.document;

import java.io.BufferedReader;
import java.io.IOException;

/**
 * A document from the GOV2 collection.
 */
public class TrecwebDocument extends TrecDocument {

  private final String DOCHDR = "<DOCHDR>";
  private final String TERMINATING_DOCHDR = "</DOCHDR>";

  @Override
  public SourceDocumentResultWrapper<TrecDocument> readNextRecord(BufferedReader reader) throws IOException {
    StringBuilder builder = new StringBuilder();
    boolean found = false;

    while (true) {
      String line = reader.readLine();
      if (line == null)
        return new SourceDocumentResultWrapper(null, SourceDocumentResultWrapper.FailureReason.EOF);

      line = line.trim();

      if (line.startsWith(DOC)) {
        found = true;
        continue;
      }

      if (line.startsWith(TERMINATING_DOC) && builder.length() > 0) {
        return new SourceDocumentResultWrapper<TrecDocument>(parseRecord(builder), null);
      }

      if (found)
        builder.append(line).append("\n");
    }
  }

  @Override
  public TrecDocument parseRecord(StringBuilder builder) {

    int i = builder.indexOf(DOCNO);
    if (i == -1) throw new RuntimeException("cannot find start tag " + DOCNO);

    if (i != 0) throw new RuntimeException("should start with " + DOCNO);

    int j = builder.indexOf(TERMINATING_DOCNO);
    if (j == -1) throw new RuntimeException("cannot find end tag " + TERMINATING_DOCNO);

    id = builder.substring(i + DOCNO.length(), j).trim();

    i = builder.indexOf(DOCHDR);
    if (i == -1) throw new RuntimeException("cannot find header tag " + DOCHDR);

    j = builder.indexOf(TERMINATING_DOCHDR);
    if (j == -1) throw new RuntimeException("cannot find end tag " + TERMINATING_DOCHDR);

    if (j < i) throw new RuntimeException(TERMINATING_DOCHDR + " comes before " + DOCHDR);

    content = builder.substring(j + TERMINATING_DOCHDR.length()).trim();

    return this;
  }
}

// # License & Terms
//
// This file is part of **Cascara**.
//
// **Cascara** is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//
// ---
//
// ## Special Runtime Exception
//
// As a special exception, the copyright holders of this library give you
// permission to link this library with independent modules to produce an
// executable, regardless of the license terms of these independent modules,
// and to copy and distribute the resulting executable under terms of your
// choice, provided that you also meet, for each linked independent module,
// the terms and conditions of the license of that module.
//
// An independent module is a module which is not derived from or based on
// this library. If you modify this library, you may extend this exception
// to your version of the library, but you are not obligated to do so. If
// you do not wish to do so, delete this exception statement from your
// version.


package io.github.qishr.cascara.lang.json.util;

import java.io.*;
import java.nio.charset.StandardCharsets;

import io.github.qishr.cascara.lang.json.processor.JsonAstParser;

public class ProfilingHarness {

    public static void main(String[] args) throws Exception {
        // Load medium.json from classpath
        InputStream inputStream = ProfilingHarness.class.getResourceAsStream("/medium.json");
        if (inputStream == null) {
            throw new FileNotFoundException("medium.json not found on classpath");
        }

        // Read entire file into a String
        StringBuilder sb = new StringBuilder(4096);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

            char[] buf = new char[8192];
            int n;
            while ((n = reader.read(buf)) != -1) {
                sb.append(buf, 0, n);
            }
        }
        String content = sb.toString();

        JsonOptions options = new JsonOptions()
            .setUseSimd(true)
            .setAllowUnicode(false)
            .setAllowComments(false)
            .setCaptureComments(false)
            .setAllowSingleQuotedStrings(false);


        for (int i = 0; i < 500_000; i++) {
            JsonAstParser parser = new JsonAstParser().setOptions(options);
            parser.parse(content);
        }
    }
}

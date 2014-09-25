/**
 *  Copyright 2011 Ryszard Wiśniewski <brut.alll@gmail.com>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package brut.androlib.src;

import brut.androlib.AndrolibException;
import brut.androlib.mod.SmaliMod;
import brut.androlib.res.util.ExtFile;
import brut.directory.DirectoryException;
import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.logging.Logger;

import org.antlr.runtime.RecognitionException;
import org.apache.commons.io.IOUtils;
import org.jf.dexlib2.writer.builder.DexBuilder;
import org.jf.dexlib2.writer.io.FileDataStore;

/**
 * @author Ryszard Wiśniewski <brut.alll@gmail.com>
 */
public class SmaliBuilder {

    public static void build(ExtFile smaliDir, File dexFile, HashMap<String, Boolean> flags)
            throws AndrolibException {
        new SmaliBuilder(smaliDir, dexFile, flags).build();
    }

    private SmaliBuilder(ExtFile smaliDir, File dexFile, HashMap<String, Boolean> flags) {
        mSmaliDir = smaliDir;
        mDexFile = dexFile;
        mFlags = flags;
    }

    private void build() throws AndrolibException {
        try {
            DexBuilder dexBuilder = DexBuilder.makeDexBuilder();

            for (String fileName : mSmaliDir.getDirectory().getFiles(true)) {
                buildFile(fileName, dexBuilder);
            }
            dexBuilder.writeTo(new FileDataStore( new File(mDexFile.getAbsolutePath())));
        } catch (IOException | DirectoryException ex) {
            throw new AndrolibException(ex);
        }
    }

    private void buildFile(String fileName, DexBuilder dexBuilder)
            throws AndrolibException, IOException {
        File inFile = new File(mSmaliDir, fileName);
        InputStream inStream = new FileInputStream(inFile);

        if (fileName.endsWith(".smali")) {
            try {
                if (!SmaliMod.assembleSmaliFile(inFile,dexBuilder, false, false)) {
                    throw new AndrolibException("Could not smali file: " + fileName);
                }
            } catch (IOException | RecognitionException ex) {
                throw new AndrolibException(ex);
            }
            return;
        }
        if (!fileName.endsWith(".java")) {
            LOGGER.warning("Unknown file type, ignoring: " + inFile);
            return;
        }

        StringBuilder out = new StringBuilder();
        List<String> lines = IOUtils.readLines(inStream);

        if (!mFlags.get("debug")) {
            final String[] linesArray = lines.toArray(new String[0]);
            for (int i = 1; i < linesArray.length - 1; i++) {
                out.append(linesArray[i].split("//", 2)[1]).append('\n');
            }
        } else {
            lines.remove(lines.size() - 1);
            ListIterator<String> it = lines.listIterator(1);

            out.append(".source \"").append(inFile.getName()).append("\"\n");
            while (it.hasNext()) {
                String line = it.next().split("//", 2)[1].trim();
                if (line.isEmpty() || line.charAt(0) == '#' || line.startsWith(".source")) {
                    continue;
                }
                if (line.startsWith(".method ")) {
                    it.previous();
                    DebugInjector.inject(it, out);
                    continue;
                }

                out.append(line).append('\n');
            }
        }

        try {
            if (!SmaliMod.assembleSmaliFile(out.toString(),dexBuilder, false, false, inFile)) {
                throw new AndrolibException("Could not smali file: " + fileName);
            }
        } catch (IOException | RecognitionException ex) {
            throw new AndrolibException(ex);
        }
    }

    private final ExtFile mSmaliDir;
    private final File mDexFile;
    private final HashMap<String, Boolean> mFlags;

    private final static Logger LOGGER = Logger.getLogger(SmaliBuilder.class.getName());
}

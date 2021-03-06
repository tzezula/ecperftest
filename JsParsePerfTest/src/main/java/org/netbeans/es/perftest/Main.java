/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2015 Oracle and/or its affiliates. All rights reserved.
 *
 * Oracle and Java are registered trademarks of Oracle and/or its affiliates.
 * Other names may be trademarks of their respective owners.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common
 * Development and Distribution License("CDDL") (collectively, the
 * "License"). You may not use this file except in compliance with the
 * License. You can obtain a copy of the License at
 * http://www.netbeans.org/cddl-gplv2.html
 * or nbbuild/licenses/CDDL-GPL-2-CP. See the License for the
 * specific language governing permissions and limitations under the
 * License.  When distributing the software, include this License Header
 * Notice in each file and include the License file at
 * nbbuild/licenses/CDDL-GPL-2-CP.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the GPL Version 2 section of the License file that
 * accompanied this code. If applicable, add the following below the
 * License Header, with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * If you wish your version of this file to be governed by only the CDDL
 * or only the GPL Version 2, indicate your decision by adding
 * "[Contributor] elects to include this software in this distribution
 * under the [CDDL or GPL Version 2] license." If you do not indicate a
 * single choice of license, a recipient has the option to distribute
 * your version of this file under either the CDDL, the GPL Version 2 or
 * to extend the choice of license to its licensees as provided above.
 * However, if you add GPL Version 2 code and therefore, elected the GPL
 * Version 2 license, then the option applies only if the new code is
 * made subject to such option by the copyright holder.
 *
 * Contributor(s):
 *
 * Portions Copyrighted 2015 Sun Microsystems, Inc.
 */
package org.netbeans.es.perftest;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Queue;
import java.util.ServiceLoader;
import java.util.Set;
import org.netbeans.es.perftest.antlr.AntlrParser;

/**
 *
 * @author Tomas Zezula
 */
public class Main {
    public static void main(final String...args) throws IOException {
        boolean printErrors = false;
        final Queue<String> parserOptions = new ArrayDeque<>();
        ParserImplementation parser = null;
        File source = null;
        boolean warmUp = false;
        File report = null;
        File progress = null;
        Integer runs = null;
        try {
            for (int i=0; i< args.length; i++) {
                switch (args[i]) {
                    case "-e": {    //NOI18N
                        printErrors = true;
                        break;
                    }
                    case "-w": {
                        warmUp = true;
                        break;
                    }
                    case "-p": {    //NOI18N
                        final String parserName = (++i < args.length) ? args[i] : null;
                        parser = parserName == null ? null : findParser(parserName);
                        break;
                    }
                    case "-r": {    //NOI18N
                        final String fileName = (++i < args.length) ? args[i] : null;
                        final File f = fileName == null ? null : new File (fileName);
                        if (f == null) {
                            throw new  IllegalArgumentException("-r");
                        }
                        report = f;
                        break;
                    }
                    case "-o": {
                        final String option = (++i < args.length) ? args[i] : null;
                        if (option == null) {
                            throw new  IllegalArgumentException("-o");
                        }
                        parserOptions.offer(option);
                        break;
                    }
                    case "-l": {    //NOI18N
                        final String fileName = (++i < args.length) ? args[i] : null;
                        final File f = fileName == null ? null : new File(fileName);
                        if (f == null) {
                            throw new IllegalArgumentException("-l");
                        }
                        progress = f;
                        break;
                    }
                    default:
                        if (source == null) {
                            source = new File (args[i]);
                        } else if (runs == null) {
                            runs = Integer.valueOf(args[i]);
                        } else {
                            throw new IllegalArgumentException(args[i]);
                        }
                }
            }
            if (parser == null) {
                parser = findParser(AntlrParser.NAME);
            }
            if (source == null || !source.canRead()) {
                throw new IllegalStateException();
            }
            if (!parserOptions.isEmpty()) {
                final Set<String> supportedOptions = parser.getOptions().keySet();
                for (String parserOption : parserOptions) {
                    parserOption = ParserOptions.splitParserArg(parserOption)[0];
                    if (!supportedOptions.contains(parserOption)) {
                        throw new IllegalArgumentException(parserOption);
                    }
                }
            }
        } catch (RuntimeException re) {
            usage();
        }
        final ParserOptions options = ParserOptions.Builder.newInstance()
                .setPrintErrors(printErrors)
                .setParserSpecificOptions(parserOptions)
                .setProgress(progress)
                .setReport(report)
                .build();
        TestRunner.Builder.newInstance(parser, options, source)
                .setRunsCount(runs == null ? 1 : runs)
                .setWarmUp(warmUp)
                .build()
                .run();
    }

    private static ParserImplementation findParser(final String name) {
        for (ParserImplementation parser : ServiceLoader.load(ParserImplementation.class)) {
            if (name.equals(parser.getName())) {
                return parser;
            }
        }
        return null;
    }

    private static void usage() {
        System.err.println("usage: JsParsePerfTest [-p parser] [-e] [-w] [-l progressFile] [-r reportFile] [-o parser specific option] source [runCount]");
        System.err.println("\t-p parser type 'antlr' or 'nashorn', the default is antlr.");
        System.err.println("\t-e print errors, default false.");
        System.err.println("\t-w warm up parser, default false.");
        System.err.println("\t-l progressFile the file to write progress into, default stdout.");
        System.err.println("\t-r reportFile the file to write report into, default stdout.");
        System.err.println("\t-o option the parser specific option.");
        System.err.println("\tsource the file or folder to parse.");
        System.err.println("\trunCount the number of test runs, default is one.");
        for (ParserImplementation parser : ServiceLoader.load(ParserImplementation.class)) {
            final Map<String,String> options = parser.getOptions();
            if (!options.isEmpty()) {
                System.err.printf("%n\tThe %s parser specic options:%n",
                        parser.getName());
                for (Map.Entry<String,String> option : options.entrySet()) {
                    System.err.printf("\t\t%s %s.%n",
                            option.getKey(),
                            option.getValue());
                }
            }
        }
        System.exit(1);
    }
}

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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.Date;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

final class TestRunner implements Runnable {
    private final ParserImplementation parser;
    private final File source;
    private final ParserOptions opts;
    private final int runs;
    private final boolean warmUp;
    private final PrintWriter progressWriter;
    private final PrintWriter reportWriter;

    private TestRunner(
            final ParserImplementation parser,
            final File source,
            final ParserOptions opts,
            final int runs,
            final boolean warmUp,
            final PrintWriter reportWriter) {
        this.parser = parser;
        this.source = source;
        this.opts = opts;
        this.runs = runs;
        this.warmUp = warmUp;
        this.progressWriter = opts.getProgressWriter();
        this.reportWriter = reportWriter;
    }


    @Override
    public void run() {
        try {
            if (warmUp) {
                progress(progressWriter, "Warm up...%n");
                files(source).forEach((f)->parse(parser, f, opts));
            }
            progress(progressWriter, "Parsing %s using %s in %d round(s).%n",
                    source.getName(),
                    parser.getName(),
                    runs);
            final long[] totalTimes = new long[runs];
            final Map<File,long[]> timesPerFile = new TreeMap<>((f1,f2) -> {
                    int res = f1.getName().compareTo(f2.getName());
                    if (res == 0) {
                        res = f1.getAbsolutePath().compareTo(f2.getAbsolutePath());
                    }
                    return res;
            });
            for (int i = 0; i < runs; i++) {
                progress(progressWriter, "Run: %d%n", 1+i);
                final int fi = i;
                totalTimes[i] = files(source)
                        .map((f)->{
                            final long t = parse(parser, f, opts);
                            progress(progressWriter, "Parsing %s took: %dms.%n",   //NOI18N
                                    f.getName(),
                                    t);
                            long[] ts = timesPerFile.get(f);
                            if (ts == null) {
                                ts = new long[runs];
                                timesPerFile.put(f, ts);
                            }
                            ts[fi] = t;
                            return t;
                        })
                        .reduce(0L, (a,b)->{return a + b;});
            }
            reportHeader(reportWriter, parser, source, warmUp);
            timesPerFile.entrySet().stream().forEach((e) -> {
                report(reportWriter, e.getKey().getName(), e.getValue());
            });
            report(reportWriter, "Whole parsing took",totalTimes);    //NOI18N
        } catch (IOException ioe) {
            TestRunner.<Void,RuntimeException>sthrow(ioe);
        }

    }

    private static void progress(
        final PrintWriter w,
        final String message,
        final Object... args) {
        w.printf(message, args);
        w.flush();
    }

    private static void reportHeader(
        final PrintWriter w,
        final ParserImplementation parser,
        final File source,
        final boolean warmUp) {
        w.println("########################################");
        w.printf("Executed: %s%n", new Date());
        w.printf("Parser: %s%s%n",
                parser.getName(),
                warmUp ? " (warmed up)" : "");
        w.printf(source.isDirectory() ?
            "Tested files in directory: %s%n" :
            "Tested file: %s%n",
                source.getName());
        w.println("########################################");
        w.flush();
    }

    private static void report(
            final PrintWriter w,
            final String message,
            final long[] times) {
        w.printf("%s:",message);           //NOI18N
        long total = 0L;
        for (int i=0; i< times.length; i++) {
            total+=times[i];
            w.printf(" %d : %dms", 1+i, times[i]); //NOI18N
        }
        w.printf("\t\t\tAvg : %dms",(total/times.length));
        w.println();
        w.flush();
    }

    private static long parse (
        final ParserImplementation parser,
        final File file,
        final ParserOptions opts) {
        long st = System.currentTimeMillis();
        try {
            parser.parse(file, opts);
        } catch (IOException ioe) {
            TestRunner.<Void,RuntimeException>sthrow(ioe);
        }
        long time =  System.currentTimeMillis() - st;
        return time;
    }

    private static Stream<? extends File> files(final File source) throws IOException {
        return source.isFile() ?
                Stream.of(source) :
                Files.walk(source.toPath())
                        .filter((p)->p.getFileName().toString().toLowerCase().endsWith(".js"))  //NOI18N
                        .map((p)->p.toFile());
    }

    private static <R,T extends Throwable> R sthrow(Throwable t) throws T {
        throw (T) t;
    }

    static final class Builder {
        private final ParserImplementation parser;
        private final File source;
        private final ParserOptions options;
        private int runs = 1;
        private boolean warmUp;
        private PrintWriter reportWriter;

        private Builder(
                final ParserImplementation parser,
                final ParserOptions options,
                final File source) {
            this.parser = parser;
            this.options = options;
            this.source = source;
            this.reportWriter = options.getProgressWriter();
        }

        Builder setRunsCount(int runs) {
            this.runs = runs;
            return this;
        }

        Builder setWarmUp(boolean warmUp) {
            this.warmUp = warmUp;
            return this;
        }

        Builder setReport(File file) throws IOException {
            this.reportWriter = file != null ?
                    new PrintWriter(new OutputStreamWriter(new FileOutputStream(file, true))) :
                    options.getProgressWriter();
            return this;
        }

        TestRunner build() {
            return new TestRunner(
                    parser,
                    source,
                    options,
                    runs,
                    warmUp,
                    reportWriter);
        }

        static Builder newInstance(
            final ParserImplementation parser,
            final ParserOptions options,
            final File source) {
            return new Builder(parser, options, source);
        }
    }
}

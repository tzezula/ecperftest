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
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.Collection;
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
            final boolean warmUp) {
        this.parser = parser;
        this.source = source;
        this.opts = opts;
        this.runs = runs;
        this.warmUp = warmUp;
        this.progressWriter = opts.getProgressWriter();
        this.reportWriter = opts.getReportWriter();
    }


    @Override
    public void run() {
        try {
            final boolean[] parserRes = new boolean[1];
            if (warmUp) {
                progress(progressWriter, "Warm up...%n");
                files(source).forEach((f)->parse(parser, f, opts, parserRes));
            }
            progress(progressWriter, "Parsing %s using %s in %d round(s).%n",
                    source.getName(),
                    parser.getName(),
                    runs);
            final long[] totalTimes = new long[runs];
            final Map<File,FileStat> timesPerFile = new TreeMap<>((f1,f2) -> {
                    int res = f1.getName().compareTo(f2.getName());
                    if (res == 0) {
                        res = f1.getAbsolutePath().compareTo(f2.getAbsolutePath());
                    }
                    return res;
            });
            parser.setUp(opts);
            for (int i = 0; i < runs; i++) {
                progress(progressWriter, "Run: %d%n", 1+i);
                final int fi = i;
                totalTimes[i] = files(source)
//                        .limit(3000)
                        .map((f)->{
                            final long t = parse(parser, f, opts, parserRes);
                            progress(progressWriter, "Parsing %s took: %dms success: %b.%n",   //NOI18N
                                    f.getName(),
                                    t,
                                    parserRes[0]);
                            FileStat ts = timesPerFile.get(f);
                            if (ts == null) {
                                ts = new FileStat(runs);
                                timesPerFile.put(f, ts);
                            }
                            ts.times[fi] = t;
                            ts.res &= parserRes[0];
                            return t;
                        })
                        .reduce(0L, (a,b)->{return a + b;});
            }
            reportHeader(reportWriter, parser, source, warmUp, opts.getParserSpecificOptions());
            timesPerFile.entrySet().stream().forEach((e) -> {
                report(reportWriter, e.getKey().getName(), e.getValue().times, e.getValue().res);
            });
            report(reportWriter, "Whole parsing took", totalTimes, null);    //NOI18N
            parser.report(opts);
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
        final boolean warmUp,
        final Collection<? extends String> options) {
        w.println("########################################");
        w.printf("Executed: %s%n", new Date());
        final StringBuilder sb = new StringBuilder();
        if (warmUp) {
            sb.append(" (warmed up");
        }
        for (String o : options) {
            if (sb.length() == 0) {
                sb.append(" (");
            } else {
                sb.append(", ");
            }
            sb.append(o);
        }
        if (sb.length()>0) {
            sb.append(')');
        }
        w.printf("Parser: %s%s%n",
                parser.getName(),
                sb);
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
            final long[] times,
            final Boolean res) {
        w.printf("%s:",message);           //NOI18N
        long total = 0L;
        for (int i=0; i< times.length; i++) {
            total+=times[i];
            w.printf(" %d : %dms", 1+i, times[i]); //NOI18N
        }
        w.printf("\t\t\tAvg : %dms",(total/times.length));
        if (res != null) {
            w.printf("\tSuccess: %b", res);
        }
        w.println();
        w.flush();
    }

    private static long parse (
        final ParserImplementation parser,
        final File file,
        final ParserOptions opts,
        final boolean[] res) {
        long st = System.currentTimeMillis();
        try {
            res[0] = parser.parse(file, opts);
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
                        .filter((p)->p.getFileName().toString().toLowerCase().endsWith(".js") && p.toFile().isFile())  //NOI18N
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

        private Builder(
                final ParserImplementation parser,
                final ParserOptions options,
                final File source) {
            this.parser = parser;
            this.options = options;
            this.source = source;
        }

        Builder setRunsCount(int runs) {
            this.runs = runs;
            return this;
        }

        Builder setWarmUp(boolean warmUp) {
            this.warmUp = warmUp;
            return this;
        }

        TestRunner build() {
            return new TestRunner(
                    parser,
                    source,
                    options,
                    runs,
                    warmUp);
        }

        static Builder newInstance(
            final ParserImplementation parser,
            final ParserOptions options,
            final File source) {
            return new Builder(parser, options, source);
        }
    }

    private static class FileStat {
        final long[] times;
        boolean res;

        FileStat(final int length) {
            this.times = new long[length];
            this.res = true;
        }
    }
}

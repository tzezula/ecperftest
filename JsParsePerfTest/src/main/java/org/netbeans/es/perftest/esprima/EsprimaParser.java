/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.netbeans.es.perftest.esprima;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleBindings;
import org.netbeans.es.perftest.ParserImplementation;
import org.netbeans.es.perftest.ParserOptions;

/**
 *
 * @author Tomas Zezula
 */
public final class EsprimaParser implements ParserImplementation {
    private static final String NAME = "esprima";   //NOI18N
    private static final String ESPRIMA_JS = "esprima.js";   //NOI18N

    private ScriptEngine jsRuntime;
    private SimpleBindings bindings;

    public EsprimaParser() {}

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Map<String, String> getOptions() {
        return Collections.emptyMap();
    }

    @Override
    public void setUp(ParserOptions options) throws IOException {
        final URL u = getClass().getClassLoader().getResource(
                String.format("%s/%s", //NOI18N
                        getClass().getPackage().getName().replace('.', '/'),    //NOI18N
                        ESPRIMA_JS));
        if (u == null) {
            throw new IllegalStateException("Cannot find esprima.js.");
        }
        final ScriptEngineManager sem = new ScriptEngineManager();
        jsRuntime = sem.getEngineByMimeType("text/javascript"); //NOI18N
        if (jsRuntime == null) {
            throw new IllegalStateException("No javascript implementation.");
        }
        bindings = new SimpleBindings(new HashMap<>());
        try (Reader in = new BufferedReader(new InputStreamReader(u.openStream(), "UTF-8"))) {  //NOI18M
            jsRuntime.eval(in, bindings);
        } catch (ScriptException se) {
            throw new IOException(se);
        }
    }

    @Override
    public boolean parse(File file, ParserOptions options) throws IOException {
        try {
            final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            Files.copy(file.toPath(), buffer);
            bindings.put("_fileContent", new String(buffer.toByteArray(), "UTF-8"));    //NOI18N
            jsRuntime.eval("JSON.stringify(esprima.parse(_fileContent),null, 2)", new SimpleBindings(bindings));
            return true;
        } catch (ScriptException ex) {
            throw new IOException(ex);
        }
    }

    @Override
    public void report(ParserOptions options) {
    }
}

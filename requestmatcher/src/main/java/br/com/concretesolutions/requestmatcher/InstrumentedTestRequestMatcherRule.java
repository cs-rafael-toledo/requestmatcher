package br.com.concretesolutions.requestmatcher;

import android.content.Context;

import java.io.IOException;
import java.io.InputStream;

public class InstrumentedTestRequestMatcherRule extends RequestMatcherRule {

    private final Context context;

    public InstrumentedTestRequestMatcherRule(Context context) {
        this.context = context;
    }

    @Override
    protected InputStream open(String path) throws IOException {
        return context.getAssets().open(path);
    }
}

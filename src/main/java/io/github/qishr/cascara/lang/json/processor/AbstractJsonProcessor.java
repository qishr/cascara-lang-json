package io.github.qishr.cascara.lang.json.processor;

import io.github.qishr.cascara.common.diagnostic.NoOpReporter;
import io.github.qishr.cascara.common.diagnostic.Reporter;
import io.github.qishr.cascara.common.lang.LanguageOptions;
import io.github.qishr.cascara.common.lang.processor.Processor;
import io.github.qishr.cascara.common.util.ContentType;
import io.github.qishr.cascara.common.util.Properties;
import io.github.qishr.cascara.lang.json.JsonOptions;

public abstract class AbstractJsonProcessor<P extends Processor> implements Processor {
    static final ContentType contentType = new ContentType("JSON")
            .withType("text/json")
            .withType("application/json")
            .withType("application/schema+json")
            .withSuffix(".json");

    protected JsonOptions options = new JsonOptions();
    protected Reporter reporter = new NoOpReporter();
    private Properties capabilities;

    protected abstract P self();

    @Override
    public Properties getServiceProperties() {
        if (capabilities == null) {
            capabilities = new Properties();
            capabilities.set("contentType", "application/json");
        }
        return capabilities;
    }

    @Override
    public ContentType getContentType() {
        return contentType;
    }

    /// {@inheritDoc}
    @Override
    public P setReporter(Reporter reporter) {
        this.reporter = reporter;
        return self();
    }

    /// {@inheritDoc}
    @Override
    public P setOptions(LanguageOptions<?> options) {
        this.options = (JsonOptions) options;
        return self();
    }
}

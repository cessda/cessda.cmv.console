/*
 * Copyright © 2017-2025 CESSDA ERIC (support@cessda.eu)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.cessda.cmv.console;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.layout.TTLLLayout;
import ch.qos.logback.classic.spi.Configurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.encoder.EncoderBase;
import ch.qos.logback.core.encoder.LayoutWrappingEncoder;
import ch.qos.logback.core.spi.ContextAwareBase;
import net.logstash.logback.encoder.LogstashEncoder;

public class LoggingConfigurator extends ContextAwareBase implements Configurator {
    @Override
    public ExecutionStatus configure(LoggerContext loggerContext) {
        addInfo("Setting up default configuration.");

        // Set up console appended
        var ca = new ConsoleAppender<ILoggingEvent>();
        ca.setContext(context);
        ca.setName("console");

        // JSON encoder
        EncoderBase<ILoggingEvent> encoder;

        boolean jsonEnabled = Boolean.parseBoolean(System.getProperty("logging.json"));

        if (jsonEnabled) {
            encoder = new LogstashEncoder();
            encoder.setContext(context);
            encoder.start();

        } else {

            var layoutEncoder = new LayoutWrappingEncoder<ILoggingEvent>();
            layoutEncoder.setContext(context);

            // same as
            // PatternLayout layout = new PatternLayout();
            // layout.setPattern("%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} -
            // %msg%n");
            TTLLLayout layout = new TTLLLayout();

            layout.setContext(context);
            layout.start();
            layoutEncoder.setLayout(layout);

            encoder = layoutEncoder;
        }

        ca.setEncoder(encoder);
        ca.start();


        // Configure root logger
        Logger rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.addAppender(ca);

        // let the caller decide
        return ExecutionStatus.NEUTRAL;
    }
}

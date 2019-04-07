package de.aaschmid.gradle.plugins.cpd.internal;

import de.aaschmid.gradle.plugins.cpd.CpdCsvFileReport;
import de.aaschmid.gradle.plugins.cpd.CpdTextFileReport;
import de.aaschmid.gradle.plugins.cpd.CpdXmlFileReport;
import net.sourceforge.pmd.cpd.CPD;
import net.sourceforge.pmd.cpd.CPDConfiguration;
import net.sourceforge.pmd.cpd.CSVRenderer;
import net.sourceforge.pmd.cpd.Language;
import net.sourceforge.pmd.cpd.LanguageFactory;
import net.sourceforge.pmd.cpd.Match;
import net.sourceforge.pmd.cpd.Renderer;
import net.sourceforge.pmd.cpd.SimpleRenderer;
import net.sourceforge.pmd.cpd.XMLRenderer;
import org.codehaus.groovy.runtime.ResourceGroovyMethods;
import org.gradle.api.GradleException;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.reporting.Report;
import org.gradle.api.reporting.SingleFileReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

public class CpdAction implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(CpdAction.class);

    private final String encoding;
    private final int minimumTokenCount;
    private final Language language;
    private final boolean skipLexicalErrors;
    private final boolean skipDuplicateFiles;
    private final Collection<File> files;
    private final List<SingleFileReport> reports;
    private final boolean ignoreFailures;

    public CpdAction(
            String encoding,
            int minimumTokenCount,
            String language,
            Properties languageProperties,
            boolean skipLexicalErrors,
            boolean skipDuplicateFiles,
            Collection<File> files,
            List<SingleFileReport> reports,
            boolean ignoreFailures
    ) {
        this.encoding = encoding;
        this.minimumTokenCount = minimumTokenCount;
        this.ignoreFailures = ignoreFailures;
        this.language = LanguageFactory.createLanguage(language, languageProperties);
        this.skipLexicalErrors = skipLexicalErrors;
        this.skipDuplicateFiles = skipDuplicateFiles;
        this.files = files;
        this.reports = reports;
    }

    @Override
    public void run() {
        CPDConfiguration cpdConfiguration = new CPDConfiguration();

        cpdConfiguration.setEncoding(encoding);
        cpdConfiguration.setMinimumTileSize(minimumTokenCount);
        cpdConfiguration.setLanguage(language);
        cpdConfiguration.setSkipLexicalErrors(skipLexicalErrors);
        cpdConfiguration.setSkipDuplicates(skipDuplicateFiles);

        CPD cpd = new CPD(cpdConfiguration);

        for (File file : files) {
            try {
                cpd.add(file);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        List<Match> matches = execute(cpd);

        for (Report report : reports) {
            writeReport(matches, report);
        }
        logResult(matches);
    }

    private List<Match> execute(CPD cpd) {
        cpd.go();

        Iterator<Match> matchesIterator = cpd.getMatches();

        List<Match> matches = new LinkedList<Match>();

        while (matchesIterator.hasNext()) {
            matches.add(matchesIterator.next());
        }
        return matches;
    }

    private void writeReport(List<Match> matches, Report report) {
        Renderer renderer = createRenderer(report);

        String render = renderer.render(matches.iterator());

        try {
            ResourceGroovyMethods.setText(report.getDestination(), render, encoding);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Renderer createRenderer(Report report) {
        if (report instanceof CpdCsvFileReport) {
            Character separator = ((CpdCsvFileReport) report).getSeparator();
            return new CSVRenderer(separator);
        } else if (report instanceof CpdTextFileReport) {
            return new SimpleRenderer();
        } else if (report instanceof CpdXmlFileReport) {
            return new XMLRenderer(encoding);
        }

        throw new IllegalArgumentException();
    }

    private void logResult(List<Match> matches) {
        if (matches.isEmpty()) {
            if (log.isInfoEnabled()) {
                log.info("No duplicates over {} tokens found.", minimumTokenCount);
            }

        } else {
            String message = "CPD found duplicate code.";
            SingleFileReport report = reports.get(0);
            if (report != null) {
                String reportUrl = report.getDestination().toString();
                message += " See the report at " + reportUrl;
            }
            if (ignoreFailures) {
                if (log.isWarnEnabled()) {
                    log.warn(message);
                }
            } else {
                throw new GradleException(message);
            }
        }
    }
}
package com.twitter.corpus.demo;

import java.io.File;
import java.io.PrintStream;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.SimpleAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.index.FieldInvertState;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.DefaultSimilarity;
import org.apache.lucene.search.Similarity;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

import com.twitter.corpus.data.Status;
import com.twitter.corpus.data.StatusBlockReader;
import com.twitter.corpus.data.StatusCorpusReader;
import com.twitter.corpus.data.StatusStream;

/**
 * Reference implementation for indexing statuses.
 */
public class IndexStatuses {
  private IndexStatuses() {}

  public static enum StatusField {
    ID("id"),
    SCREEN_NAME("screen_name"),
    CREATED_AT("create_at"),
    TEXT("text");

    public final String name;

    StatusField(String s) {
      name = s;
    }
  };

  private static final String INPUT_OPTION = "input";
  private static final String INDEX_OPTION = "index";

  @SuppressWarnings("static-access")
  public static void main(String[] args) throws Exception {
    Options options = new Options();
    options.addOption(OptionBuilder.withArgName("path").hasArg()
        .withDescription("input directory or file").create(INPUT_OPTION));
    options.addOption(OptionBuilder.withArgName("path").hasArg()
        .withDescription("index location").create(INDEX_OPTION));

    CommandLine cmdline = null;
    CommandLineParser parser = new GnuParser();
    try {
      cmdline = parser.parse(options, args);
    } catch (ParseException exp) {
      System.err.println("Error parsing command line: " + exp.getMessage());
      System.exit(-1);
    }

    if (!cmdline.hasOption(INPUT_OPTION) || !cmdline.hasOption(INDEX_OPTION)) {
      HelpFormatter formatter = new HelpFormatter();
      formatter.printHelp(IndexStatuses.class.getName(), options);
      System.exit(-1);
    }

    File indexLocation = new File(cmdline.getOptionValue(INDEX_OPTION));

    File file = new File(cmdline.getOptionValue(INPUT_OPTION));
    if (!file.exists()) {
      System.err.println("Error: " + file + " does not exist!");
      System.exit(-1);
    }

    PrintStream out = new PrintStream(System.out, true, "UTF-8");

    StatusStream stream;
    if (file.isDirectory()) {
      stream = new StatusCorpusReader(file);
    } else {
      stream = new StatusBlockReader(file);
    }

    Analyzer analyzer = new SimpleAnalyzer(Version.LUCENE_31);
    Similarity similarity = new ConstantNormSimilarity();
    IndexWriterConfig config = new IndexWriterConfig(Version.LUCENE_31, analyzer);
    config.setSimilarity(similarity);

    IndexWriter writer = new IndexWriter(FSDirectory.open(indexLocation), config);

    int cnt = 0;
    Status status;
    while ((status = stream.next()) != null) {
      cnt++;
      Document doc = new Document();
      doc.add(new Field(StatusField.ID.name, status.getId()+"", Store.YES, Index.NOT_ANALYZED_NO_NORMS));
      doc.add(new Field(StatusField.SCREEN_NAME.name, status.getScreenname(), Store.YES, Index.NOT_ANALYZED_NO_NORMS));
      doc.add(new Field(StatusField.CREATED_AT.name, status.getCreatedAt(), Store.YES, Index.NO));
      doc.add(new Field(StatusField.TEXT.name, status.getText(), Store.YES, Index.ANALYZED));

      writer.addDocument(doc);
      if (cnt % 10000 == 0) {
        out.println(cnt + " statuses indexed");
      }
    }
    out.println("Optimizing index...");
    writer.optimize();
    writer.close();
    out.println(String.format("Total of %s statuses indexed", cnt));
  }

  public static class ConstantNormSimilarity extends DefaultSimilarity {
    private static final long serialVersionUID = 2737920231537795826L;
    @Override
    public float computeNorm(String field, FieldInvertState state) {
      return 1.0f;
    }
  }
}
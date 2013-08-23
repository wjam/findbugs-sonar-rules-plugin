package com.github.wjam.plugin.findbugs;

import com.github.wjam.findbugs.BugPatternType;
import com.github.wjam.findbugs.MessageCollectionType;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mojo(name = "sonarRules", defaultPhase = LifecyclePhase.GENERATE_RESOURCES, threadSafe = true)
public class FindBugsSonarMojo extends AbstractMojo {

  private static final Pattern BUG_RANK = Pattern.compile("^(-?\\d*) [^ ]* (.*?)$");
  private static final String SONAR_RULES_HEADER_PRE_CHARSET = "<?xml version=\"1.0\" encoding=\"";
  private static final String SONAR_RULES_HEADER_POST_CHARSET = "\"?>\n<rules>\n";
  private static final String SONAR_RULES_FOOTER = "</rules>\n";

  private static final String CLASSPATH  = "classpath:";
  private static final JAXBContext CONTEXT;

  static {
    try {
      CONTEXT = JAXBContext.newInstance(BugPatternType.class.getPackage().getName());
    } catch (JAXBException e) {
      throw new RuntimeException(e);
    }
  }

  @Parameter(defaultValue = "${project.basedir}/src/main/resources/messages.xml")
  private String findBugsMessagesXml;

  @Parameter(defaultValue = "${project.basedir}/src/main/resources/bugrank.txt")
  private String findBugsBugRankTxt;

  @Parameter(defaultValue = "")
  private String nameSuffix;

  @Parameter(defaultValue = "${project.build.directory}/sonar-rules/rules.xml")
  private File sonarRulesXml;

  @Parameter(defaultValue = "${project.build.sourceEncoding}", property = "encoding")
  private Charset charset;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    // Look at ear:generate-application-xml

    final Map<String, String> bugRanks = parseBugRankFile(getBugRankFile());
    final Iterable<BugPatternType> bugPatterns = getBugPatterns(readMessagesFile(getMessagesFile()));

    startRulesFile();
    for (final BugPatternType bug : bugPatterns) {
      appendRule(bug.getType(), bugRanks.get(bug.getType()), bug.getShortDescription(), bug.getDetails());
    }
    endRulesFile();

  }

  private void startRulesFile() throws MojoExecutionException {
    try {
      Files.write(SONAR_RULES_HEADER_PRE_CHARSET + charset.displayName() + SONAR_RULES_HEADER_POST_CHARSET,
          sonarRulesXml, charset);
    } catch (IOException e) {
      throw new MojoExecutionException("Unable to write to file " + sonarRulesXml, e);
    }
  }

  private void appendRule(final String key, final String priority, final String name, final String description)
      throws MojoExecutionException {
    final String xml =
        "  <rule key=\"" + key +"\" priority=\"" + priority +"\">\n" +
        "    <name><![CDATA[" + name + nameSuffix + "]]></name>\n" +
        "    <configKey><![CDATA[" + key +"]]></configKey>\n" +
        "    <description><![CDATA[" + description + "]]></description>\n" +
        "  </rule>";
    try {
      Files.append(xml, sonarRulesXml, charset);
    } catch (IOException e) {
      throw new MojoExecutionException("Unable to write to file " + sonarRulesXml, e);
    }
  }

  private void endRulesFile() throws MojoExecutionException {
    try {
      Files.append(SONAR_RULES_FOOTER, sonarRulesXml, charset);
    } catch (IOException e) {
      throw new MojoExecutionException("Unable to write to file " + sonarRulesXml, e);
    }
  }

  private Map<String, String> parseBugRankFile(final URL bugRankFile) throws MojoExecutionException {
    final Map<String, String> ret = new HashMap<String, String>();
    try {
      for (final String s : Resources.readLines(bugRankFile, charset)) {
        final Matcher m = BUG_RANK.matcher(s);
        if (m.matches()) {
          final String priority = bugRankToPriority(m.group(1));
          if (priority != null) {
            ret.put(m.group(2), priority);
          }
        }
      }
    } catch (IOException e) {
      throw new MojoExecutionException("Unable to read bugrank file " + findBugsBugRankTxt, e);
    }
    return ret;
  }

  private String bugRankToPriority(final String rank) {
    /*

Bug rankers are used to compute a bug rank for each bug instance. Bug ranks 1-20 are for bugs that are visible to users.
 Bug rank 1 is more the most relevant/scary bugs. A bug rank greater than 20 is for issues that should not be shown to
  users. The following bug rankers may exist:

    core bug ranker (loaded from etc/bugrank.txt)
    a bug ranker for each plugin (loaded from /etc/bugrank.txt)
    A global adjustment ranker (loaded from plugins/adjustBugrank.txt)

A bug ranker is comprised of a list of bug patterns, bug kinds and bug categories. For each, either an absolute or
 relative bug rank is provided. A relative rank is one preceeded by a + or -. For core bug detectors, the bug ranker search order is:

    global adjustment bug ranker
    core bug ranker

For third party plugins, the bug ranker search order is:

    global adjustment bug ranker
    plugin adjustment bug ranker
    core bug ranker

The overall search order is

    Bug patterns, in search order across bug rankers
    Bug kinds, in search order across bug rankers
    Bug categories, in search order across bug rankers

Search stops at the first absolute bug rank found, and the result is the sum of all of relative bug ranks plus the
 final absolute bug rank. Since all bug categories are defined by the core bug ranker, we should always find an absolute bug rank.
     */
    // INFO, MINOR, MAJOR, CRITICAL, BLOCKER;
    return "MINOR";  //To change body of created methods use File | Settings | File Templates.
  }

  private URL getBugRankFile() throws MojoExecutionException {
    return getFile(findBugsBugRankTxt);
  }

  private Iterable<BugPatternType> getBugPatterns(final MessageCollectionType messages) {
    return Iterables.filter(messages.getContent(), BugPatternType.class);
  }

  private URL getMessagesFile() throws MojoExecutionException {
    return getFile(findBugsMessagesXml);
  }

  private URL getFile(final String file) throws MojoExecutionException {
    try {
      if (file.startsWith(CLASSPATH)) {
        return Resources.getResource(removeStart(file, CLASSPATH));
      } else {
        return new File(file).toURI().toURL();
      }
    } catch (IOException e) {
      throw new MojoExecutionException("Unable to read '" + file + "' file on class path", e);
    }
  }

  private MessageCollectionType readMessagesFile(final URL xml) throws MojoExecutionException {
    try {
      return (MessageCollectionType) CONTEXT.createUnmarshaller().unmarshal(xml);
    } catch (JAXBException e) {
      throw new MojoExecutionException("Unable to parse " + xml, e);
    }
  }

  private String removeStart(final String s, final String start) {
    return s.substring(start.length());
  }
}

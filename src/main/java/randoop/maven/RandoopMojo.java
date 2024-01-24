package randoop.maven;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Mojo(name = "gentests", defaultPhase = LifecyclePhase.GENERATE_TEST_SOURCES, threadSafe = true, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class RandoopMojo extends AbstractMojo {

	@Parameter(required = true)
	private String packageName;

	@Parameter(required = true, defaultValue = "${project.build.outputDirectory}")
	private String sourceDirectory;

	@Parameter(required = true, defaultValue = "${project.build.directory}/generated-test-sources/java")
	private String targetDirectory;

	@Parameter(required = true, defaultValue = "30")
	private int timeoutInSeconds;

	@Component
	private MavenProject project;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {

		// Collect class and jars for class path
		final List<URL> urls = new LinkedList<>();
		urls.add(loadProjectClasses());
		urls.addAll(loadProjectDependencies(project));
		urls.add(loadPluginJarWithRandoop());

		final List<String> args = buildArgs(urls);

		final String randoopCommandLine = args.stream().collect(Collectors.joining(" "));
//		randoopCommandLine.replaceAll("file:/", "");
		getLog().info("Call outside Maven: " + randoopCommandLine);

		runRandoopGenTests(args);
	}

	private List<String> buildArgs(final List<URL> urls) {
		String classPath = urls.stream().map(u -> u.toString()).collect(Collectors.joining(File.pathSeparator));
		classPath = classPath.replaceAll("file:/", "");
		// Build up Randoop command line
		final List<String> args = new LinkedList<>();
		args.add("java");
		args.add("-ea");
		args.add("-classpath");
		args.add(classPath);
		args.add("randoop.main.Main");
		args.add("gentests");
		args.add("--time-limit=" + timeoutInSeconds);
		args.add("--debug-checks=true");
		args.add("--junit-package-name=" + packageName);
		args.add("--junit-output-dir=" + targetDirectory);

		// Add project classes
		final URLClassLoader classLoader = new URLClassLoader(convert(urls));
		List<Class<?>> allClassesOfPackage = ClassFinder.find(packageName, classLoader);
		for (Class<?> currentClass : allClassesOfPackage) {
			getLog().info("Add class " + currentClass.getName());
			args.add("--testclass=" + currentClass.getName());
		}
		return args;
	}

	private void runRandoopGenTests(final List<String> args) throws MojoExecutionException {
		ProcessBuilder processBuilder = new ProcessBuilder(args);
		processBuilder.redirectErrorStream(true);
		processBuilder.redirectOutput(ProcessBuilder.Redirect.PIPE);
		processBuilder.directory(project.getBasedir());
		try {
			Process randoopProcess = processBuilder.start();
			getLog().info("Randoop started with time limit of " + timeoutInSeconds + " seconds.");
			randoopProcess.waitFor();
//			randoopProcess.waitFor(timeoutInSeconds + timeoutInSeconds, TimeUnit.SECONDS);
			if (randoopProcess.exitValue() != 0) {
				throw new MojoFailureException(this, "Randoop encountered an error!",
						"Failed to generate " + "test, exit value is " + randoopProcess.exitValue());
			}
		} catch (Exception e) {
			throw new MojoExecutionException(e.getMessage(), e);
		}
	}

	private URL loadPluginJarWithRandoop() {
		return getClass().getProtectionDomain().getCodeSource().getLocation();
	}

	private static List<URL> loadProjectDependencies(final MavenProject project) throws MojoExecutionException {
		final List<URL> urls = new LinkedList<>();
		try {
			for (Artifact artifact : project.getArtifacts()) {
				urls.add(artifact.getFile().toURI().toURL());
			}
		} catch (MalformedURLException e) {
			throw new MojoExecutionException("Could not add artifact!", e);
		}
		return urls;
	}

	private URL loadProjectClasses() throws MojoExecutionException {
		final URL source;
		try {
			source = createUrlFrom(sourceDirectory);
		} catch (MalformedURLException e) {
			throw new MojoExecutionException("Could not create source path!", e);
		}
		return source;
	}

	private static URL[] convert(final Collection<URL> urls) {
		return urls.toArray(new URL[urls.size()]);
	}

	private static URL createUrlFrom(final String path) throws MalformedURLException {
		return new File(path).toURI().toURL();
	}
	
	public static void main(String[] args) {
		final String randoopCommandLine ="java -ea -classpath file:/C:/Users/leona/Downloads/randoop/gitprojects/greplin-zookeeper-utils/target/classes/;file:/C:/Users/leona/.m2/repository/org/apache/zookeeper/zookeeper/3.4.5/zookeeper-3.4.5.jar;file:/C:/Users/leona/.m2/repository/jline/jline/0.9.94/jline-0.9.94.jar;file:/C:/Users/leona/.m2/repository/org/jboss/netty/netty/3.2.2.Final/netty-3.2.2.Final.jar;file:/C:/Users/leona/.m2/repository/com/google/guava/guava/14.0.1/guava-14.0.1.jar;file:/C:/Users/leona/.m2/repository/commons-io/commons-io/1.3.2/commons-io-1.3.2.jar;file:/C:/Users/leona/.m2/repository/org/slf4j/slf4j-api/1.7.4/slf4j-api-1.7.4.jar;file:/C:/Users/leona/.m2/repository/randoop/randoop-all/4.3.2/randoop-all-4.3.2.jar;file:/C:/Users/leona/.m2/repository/randoop/randoop-maven-plugin/4.3.2/randoop-maven-plugin-4.3.2.jar randoop.main.Main gentests --time-limit=30 --debug-checks=true --junit-package-name=com.greplin.zookeeper --junit-output-dir=C:\\Users\\leona\\Downloads\\randoop\\gitprojects\\greplin-zookeeper-utils\\target/generated-test-sources/java --testclass=com.greplin.zookeeper.EmbeddedZookeeperServer$1 --testclass=com.greplin.zookeeper.EmbeddedZookeeperServer$Builder --testclass=com.greplin.zookeeper.EmbeddedZookeeperServer --testclass=com.greplin.zookeeper.RobustZooKeeper$1 --testclass=com.greplin.zookeeper.RobustZooKeeper$2 --testclass=com.greplin.zookeeper.RobustZooKeeper$ConnectionWatcher --testclass=com.greplin.zookeeper.RobustZooKeeper";
		randoopCommandLine.replace("file:/", "");
		System.out.println(randoopCommandLine);
	}
}

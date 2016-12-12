
import org.gradle.BuildAdapter;
import org.gradle.BuildResult;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.execution.TaskExecutionListener;
import org.gradle.api.tasks.TaskState;
import org.gradle.internal.UncheckedException;


import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class GradleTracingPlugin implements Plugin<Project> {
    final List<String> events = new ArrayList<>();

    @Override
    public void apply(Project project) {
        File jsonDir = new File(project.getProjectDir(), "tracing");
        File jsonFile = new File(jsonDir, "trace.json");

        jsonDir.mkdirs();

        final PrintWriter writer = getPrintWriter(jsonFile);


        project.getGradle().getTaskGraph().addTaskExecutionListener(new TaskExecutionListener() {
            @Override
            public void beforeExecute(Task task) {
                events.add("{\"name\": \"" + task.getName() + "\", \"cat\": \"PERF\", \"ph\": \"B\", \"pid\": 0, \"tid\": " + Thread.currentThread().getId() + ", \"ts\": " + System.currentTimeMillis() + "}");
            }

            @Override
            public void afterExecute(Task task, TaskState taskState) {
                events.add("{\"name\": \"" + task.getName() + "\", \"cat\": \"PERF\", \"ph\": \"E\", \"pid\": 0, \"tid\": " + Thread.currentThread().getId() + ", \"ts\": " + System.currentTimeMillis() + "}");
            }
        });

        project.getGradle().addBuildListener(new JsonAdapter(writer));
    }

    private PrintWriter getPrintWriter(File jsonFile) {
        try {
            return new PrintWriter(new FileWriter(jsonFile), true);
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private class JsonAdapter extends BuildAdapter {
        private final PrintWriter writer;

        public JsonAdapter(PrintWriter writer) {
            this.writer = writer;
        }

        @Override
        public void buildFinished(BuildResult result) {
            writer.println("{\n" +
                    "  \"traceEvents\": [\n");

            writer.println(String.join(",", events));
            writer.println("],\n" +
                    "  \"displayTimeUnit\": \"ms\",\n" +
                    "  \"systemTraceEvents\": \"SystemTraceData\",\n" +
                    "  \"otherData\": {\n" +
                    "    \"version\": \"My Application v1.0\"\n" +
                    "  }\n" +
                    "}\n");
        }
    }
}

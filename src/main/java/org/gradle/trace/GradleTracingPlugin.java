package org.gradle.trace;

import org.gradle.BuildAdapter;
import org.gradle.BuildResult;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.execution.TaskExecutionListener;
import org.gradle.api.tasks.TaskState;
import org.gradle.internal.UncheckedException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.StringJoiner;

public class GradleTracingPlugin implements Plugin<Project> {
    final List<TraceEvent> events = new ArrayList<>();
    private long startTimeStamp;

    @Override
    public void apply(Project project) {
        startTimeStamp = System.nanoTime();

        project.getGradle().getTaskGraph().addTaskExecutionListener(new TaskExecutionListener() {
            @Override
            public void beforeExecute(Task task) {
                events.add(TraceEvent.started(task.getPath(), "TASK"));
            }

            @Override
            public void afterExecute(Task task, TaskState taskState) {
                events.add(TraceEvent.finished(task.getPath(), "TASK"));
            }
        });

        project.getGradle().addBuildListener(new JsonAdapter(project.getBuildDir()));
    }

    private PrintWriter getPrintWriter(File jsonFile) {
        try {
            return new PrintWriter(new FileWriter(jsonFile), true);
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private class JsonAdapter extends BuildAdapter {
        private final File buildDir;

        public JsonAdapter(File buildDir) {
            this.buildDir = buildDir;
        }

        @Override
        public void buildFinished(BuildResult result) {
            File traceFile = getTraceFile(buildDir);
            PrintWriter writer = getPrintWriter(traceFile);
            writer.println("{\n" +
                    "  \"traceEvents\": [\n");

            Iterator<TraceEvent> itr = events.iterator();
            while (itr.hasNext()) {
                writer.print(itr.next().toString());
                writer.println(itr.hasNext() ? "," : "");
            }

            writer.println("],\n" +
                    "  \"displayTimeUnit\": \"ns\",\n" +
                    "  \"systemTraceEvents\": \"SystemTraceData\",\n" +
                    "  \"otherData\": {\n" +
                    "    \"version\": \"My Application v1.0\"\n" +
                    "  }\n" +
                    "}\n");
        }
    }

    private File getTraceFile(File buildDir) {
        File jsonDir = new File(buildDir, "trace");
        jsonDir.mkdirs();
        return new File(jsonDir, "task-trace.json");
    }
}

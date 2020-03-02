package com.oppo.tagbase.jobv2;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Range;
import com.oppo.tagbase.common.util.Uuid;
import com.oppo.tagbase.meta.obj.Job;
import com.oppo.tagbase.meta.obj.JobState;
import com.oppo.tagbase.meta.obj.JobType;
import com.oppo.tagbase.meta.obj.Slice;
import com.oppo.tagbase.meta.obj.Task;
import com.oppo.tagbase.meta.obj.TaskState;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Created by wujianchao on 2020/2/28.
 */
public class JobUtil {

    public static final String BUILDING_BITMAP_TASK = "BUILDING_BITMAP_TASK";
    public static final String LOAD_BITMAP_TO_STORAGE_TASK = "LOAD_BITMAP_TO_STORAGE_TASK";

    public static final String BUILDING_INVERTED_DICT_TASK = "BUILDING_INVERTED_DICT_TASK";
    public static final String BUILDING_FORWARD_DICT_TASK = "BUILDING_FORWARD_DICT_TASK";

    public static String taskNamePrefix(Job job) {
        return job.getName();
    }

    public static Task previousTask(List<Task> taskList, Task current) {
        byte step = current.getStep();
        if(step == 0) {
            return null;
        }
        return taskList.get(step - 1);
    }

    public static Task previousTask(Job job, Task current) {
        return previousTask(job.getTasks(), current);
    }

    public static Job newDataJob( String dbName, String tableName, LocalDateTime dataLowerTime, LocalDateTime dataUpperTime) {
        Job job = new Job();
        job.setId(Uuid.nextId());
        job.setName(JobUtil.makeDataJobName(dbName, tableName, dataLowerTime, dataUpperTime));
        job.setDbName(dbName);
        job.setTableName(tableName);
        job.setDataLowerTime(dataLowerTime);
        job.setDataUpperTime(dataUpperTime);
        job.setCreateTime(LocalDateTime.now());
        job.setState(JobState.PENDING);
        job.setType(JobType.DATA);
        return job;
    }

    public static Job newDictJob(LocalDateTime dataLowerTime, LocalDateTime dataUpperTime) {
        Job job = new Job();
        job.setName(JobUtil.makeDictJobName(dataLowerTime, dataUpperTime));
        job.setId(Uuid.nextId());
        job.setState(JobState.PENDING);
        job.setCreateTime(LocalDateTime.now());
        job.setDataLowerTime(dataLowerTime);
        job.setDataUpperTime(dataUpperTime);
        job.setType(JobType.DICTIONARY);
        addTasksToJob(job);
        return job;
    }


    static void addTasksToJob(Job job) {

        switch (job.getType()) {
            case DATA:
                makeDataJobTasks(job);
                break;
            case DICTIONARY:
                makeDictJobTasks(job);
                break;
            default:
                throw new JobException("Illegal job type: " + job.getType().name());
        }
    }

    static void makeDataJobTasks(Job job) {
        Task buildingBitmapTask = new Task();
        buildingBitmapTask.setId(Uuid.nextId());
        buildingBitmapTask.setState(TaskState.PENDING);
        buildingBitmapTask.setStep((byte) 0);
        buildingBitmapTask.setName(taskNamePrefix(job) + "_" + BUILDING_BITMAP_TASK);
        buildingBitmapTask.setId(job.getId());

        Task loadDataToStorageTask = new Task();
        loadDataToStorageTask.setId(Uuid.nextId());
        loadDataToStorageTask.setState(TaskState.PENDING);
        loadDataToStorageTask.setStep((byte) 1);
        loadDataToStorageTask.setName(taskNamePrefix(job) + "_" + LOAD_BITMAP_TO_STORAGE_TASK);
        loadDataToStorageTask.setId(job.getId());

        job.setTasks(Lists.newArrayList(buildingBitmapTask, loadDataToStorageTask));
    }

    static void makeDictJobTasks(Job job) {
        Task buildingInvertedDictTask = new Task();
        buildingInvertedDictTask.setId(Uuid.nextId());
        buildingInvertedDictTask.setState(TaskState.PENDING);
        buildingInvertedDictTask.setStep((byte) 0);
        buildingInvertedDictTask.setName(taskNamePrefix(job) + "_" + BUILDING_INVERTED_DICT_TASK);
        buildingInvertedDictTask.setId(job.getId());

        Task loadDataToStorageTask = new Task();
        loadDataToStorageTask.setId(Uuid.nextId());
        loadDataToStorageTask.setState(TaskState.PENDING);
        loadDataToStorageTask.setStep((byte) 1);
        loadDataToStorageTask.setName(taskNamePrefix(job) + "_" + BUILDING_FORWARD_DICT_TASK);
        loadDataToStorageTask.setId(job.getId());

        job.setTasks(Lists.newArrayList(buildingInvertedDictTask, loadDataToStorageTask));
    }

    public static String makeForwardDictName() {
        return System.getProperty("user.dir")
                + File.separator
                + "dict"
                + File.separator
                + "dict-forward-"
                + LocalDate.now()
                + ThreadLocalRandom.current().nextInt(100)
                ;
    }

    public static String makeDictJobName(LocalDateTime dataLowerTime, LocalDateTime dataUpperTime) {
        return Joiner.on("_").join("DICt", dataLowerTime, dataUpperTime);
    }

    public static String makeDataJobName(String dbName, String tableName, LocalDateTime dataLowerTime, LocalDateTime dataUpperTime) {
        return Joiner.on("_").join(dbName, tableName, dataLowerTime, dataUpperTime);
    }

    public static Timeline makeJobTimeline(List<Job> jobList) {
        return Timeline.of(jobList.stream()
                .sorted()
                .map(job -> Range.closedOpen(job.getDataLowerTime(), job.getDataUpperTime()))
                .collect(Collectors.toCollection(TreeSet::new)));
    }

    public static Timeline makeSliceTimeline(List<Slice> sliceList) {
        return Timeline.of(sliceList.stream()
                .sorted()
                .map(job -> Range.closedOpen(job.getStartTime(), job.getEndTime()))
                .collect(Collectors.toCollection(TreeSet::new)));
    }

}

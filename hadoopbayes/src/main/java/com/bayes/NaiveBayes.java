package com.bayes;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IOUtils;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.SequenceFileInputFormat;
import org.apache.hadoop.mapreduce.lib.jobcontrol.ControlledJob;
import org.apache.hadoop.mapreduce.lib.jobcontrol.JobControl;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.SequenceFileOutputFormat;
import org.apache.hadoop.util.ReflectionUtils;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * @Author: Konfuse
 * @Date: 19-3-22 下午7:36
 */
public class NaiveBayes {
    static String[] outArgs;

    //计算INT型值的集合，返回所有值的和
    private static IntWritable calcSum(Iterable<IntWritable> values) {
        IntWritable result = new IntWritable();
        int sum = 0;
        for (IntWritable value: values) {
            sum += value.get();
        }
        result.set(sum);
        return result;
    }

    /*
     * 第一个MapReduce用于处理序列化的文件，得到<<类名:单词>,单词出现次数>,即<<Class:word>,TotalCounts>
     * 输入:args[0],序列化的训练集,key为<类名:文档名>,value为文档中对应的单词.形式为<<ClassName:Doc>,word1 word2...>
     * 输出:args[1],key为<类名:单词>,value为单词出现次数,即<<Class:word>,TotalCounts>
     */
    public static class ClassWordCountsMap extends Mapper<Text, Text, Text, IntWritable> {
        private Text newKey = new Text();
        private final IntWritable one = new IntWritable(1);

        @Override
        protected void map(Text key, Text value, Context context) throws IOException, InterruptedException {
            String cls = key.toString().split(":")[0];
            StringTokenizer stringTokenizer = new StringTokenizer(value.toString());
            while (stringTokenizer.hasMoreTokens()) {
                newKey.set(cls + ":" + stringTokenizer.nextToken());
                context.write(newKey, one);
            }
        }
    }

    public static class ClassWordCountsReduce extends Reducer<Text, IntWritable, Text, IntWritable> {
        @Override
        protected void reduce(Text key, Iterable<IntWritable> values, Context context) throws IOException, InterruptedException {
            context.write(key, calcSum(values));
        }
    }

    /*
     * 第二个MapReduce在第一个MapReduce计算的基础上进一步得到每个类的单词总数<class,TotalWords>
     * 输入:args[1],输入格式为<<class:word>,counts>
     * 输出:args[2],输出key为类名,value为单词总数.格式为<class,Totalwords>
     */
    public static class ClassTotalWordsMap extends Mapper<Text, IntWritable, Text, IntWritable> {
        private Text newKey = new Text();

        @Override
        protected void map(Text key, IntWritable value, Context context) throws IOException, InterruptedException {
            newKey.set(key.toString().split(":")[0]);
            context.write(newKey, value);
        }
    }

    public static class ClassTotalWordsReduce extends Reducer<Text, IntWritable, Text, IntWritable> {
        @Override
        protected void reduce(Text key, Iterable<IntWritable> values, Context context) throws IOException, InterruptedException {
            context.write(key, calcSum(values));
        }
    }

    /*
	 * 第三个MapReduce在第一个MapReduce的计算基础上得到训练集中不重复的单词<word,one>
	 * 输入:args[1],输入格式为<<class,word>,counts>
	 * 输出:args[3],输出key为不重复单词,value为1.格式为<word,one>
	 */
    public static class DiffTotalWordsMap extends Mapper<Text, IntWritable, Text, IntWritable> {
        private Text newKey = new Text();

        @Override
        protected void map(Text key, IntWritable value, Context context) throws IOException, InterruptedException {
            newKey.set(key.toString().split(":")[1]);
            context.write(newKey, value);
        }
    }

    public static class DiffTotalWordsReduce extends Reducer<Text, IntWritable, Text, IntWritable> {
        private IntWritable one = new IntWritable(1);

        @Override
        protected void reduce(Text key, Iterable<IntWritable> values, Context context) throws IOException, InterruptedException {
            context.write(key, one);
        }
    }

    /* 计算先验概率
	 * 先验概率P(c)=类c下的单词总数/整个训练样本的单词总数
	 * 输入:对应第二个MapReduce的输出,格式为<class,totalWords>
	 * 输出:得到HashMap<String,Double>,即<类名,概率>
	 */
    private static HashMap<String, Double> classProbably = new HashMap<String, Double>();
    public static  HashMap<String, Double> GetPriorProbably() throws IOException {
        Configuration configuration = new Configuration();
        String filePath = outArgs[2] + "/part-r-00000";
        FileSystem fs = FileSystem.get(URI.create(filePath), configuration);
        Path path = new Path(filePath);
        SequenceFile.Reader reader = null;
        double totalWords = 0;
        try {
            reader = new SequenceFile.Reader(fs, path, configuration);
            Text key = (Text) ReflectionUtils.newInstance(reader.getKeyClass(), configuration);
            IntWritable value = (IntWritable) ReflectionUtils.newInstance(reader.getValueClass(), configuration);
            long position = reader.getPosition();
            while (reader.next(key, value)) {
                totalWords += value.get();
            }
            reader.seek(position);
            while (reader.next(key, value)) {
                classProbably.put(key.toString(), value.get() / totalWords);
            }
        } finally {
            IOUtils.closeStream(reader);
        }
        return classProbably;
    }

    /* 计算条件概率
	 * 条件概率P(tk|c)=(类c下单词tk在各个文档中出现过的次数之和+1)/（类c下单词总数+训练样本中不重复特征词总数）
	 * 输入:对应第一个MapReduce的输出<<class:word>,counts>,第二个MapReduce的输出<class,totalWords>,第三个MapReduce的输出<word,one>
	 * 输出:得到HashMap<String,Double>,即<<类名:单词>,概率>
	 */
    private static HashMap<String, Double> wordsProbably = new HashMap<String, Double>();
    public static HashMap<String, Double> GetConditionProbably() throws IOException {
        Configuration configuration = new Configuration();
        String classWordCountsPath = outArgs[1] + "/part-r-00000";
        String classTotalWordsPath = outArgs[2] + "/part-r-00000";
        String difffTotalWordsPath = outArgs[3] + "/part-r-00000";
        double totalDiffWords = 0.0;
        HashMap<String, Double> classTotalWordsMap = new HashMap<String, Double>();

        //计算每个类包含的所有单词数量，放入map中
        FileSystem fs1 = FileSystem.get(URI.create(classTotalWordsPath), configuration);
        Path path1 = new Path(classTotalWordsPath);
        SequenceFile.Reader reader1 = null;
        try {
            reader1 = new SequenceFile.Reader(fs1, path1, configuration);
            Text key = (Text) ReflectionUtils.newInstance(reader1.getKeyClass(), configuration);
            IntWritable value = (IntWritable) ReflectionUtils.newInstance(reader1.getValueClass(), configuration);
            while (reader1.next(key, value)) {
                classTotalWordsMap.put(key.toString(), value.get() * 1.0);
            }
        } finally {
            IOUtils.closeStream(reader1);
        }

        //计算每个类下不重复单词的数量
        FileSystem fs2 = FileSystem.get(URI.create(difffTotalWordsPath), configuration);
        Path path2 = new Path(difffTotalWordsPath);
        SequenceFile.Reader reader2 = null;
        try {
            reader2 = new SequenceFile.Reader(fs2, path2, configuration);
            Text key = (Text) ReflectionUtils.newInstance(reader2.getKeyClass(), configuration);
            IntWritable value = (IntWritable) ReflectionUtils.newInstance(reader2.getValueClass(), configuration);
            while (reader2.next(key, value)) {
                totalDiffWords += value.get();
            }
        } finally {
            IOUtils.closeStream(reader2);
        }

        //计算条件概率P(tk|c)=(类c下单词tk在各个文档中出现过的次数之和+1)/（类c下单词总数+训练样本中不重复特征词总数）
        FileSystem fs3 = FileSystem.get(URI.create(classWordCountsPath), configuration);
        Path path3 = new Path(classWordCountsPath);
        SequenceFile.Reader reader3 = null;
        try {
            reader3 = new SequenceFile.Reader(fs3, path3, configuration);
            Text key = (Text) ReflectionUtils.newInstance(reader3.getKeyClass(), configuration);
            IntWritable value = (IntWritable) ReflectionUtils.newInstance(reader3.getValueClass(), configuration);
            String newKey = null;
            int index;
            while (reader3.next(key, value)) {
                index = key.toString().indexOf(":");
                newKey = key.toString().substring(0, index);
                wordsProbably.put(key.toString(), (value.get() + 1) / (classTotalWordsMap.get(newKey) + totalDiffWords));
            }
            //对于同一个类别没有出现过的单词的概率一样，1/(ClassTotalWords.get(class) + TotalDiffWords)
            //遍历类，每个类别中再加一个没有出现单词的概率，其格式为<class,probably>
            for (Map.Entry<String, Double> entry : classTotalWordsMap.entrySet()) {
                wordsProbably.put(entry.getKey(), entry.getValue() + totalDiffWords);
            }
        } finally {
            IOUtils.closeStream(reader3);
        }

        return wordsProbably;
    }

    /*
	 * 第四个MapReduce进行贝叶斯测试
	 * 输入:args[4],测试文件的路径,测试数据格式<<class:doc>,word1 word2 ...>
	 *      HashMap<String,Double> classProbably先验概率
     *      HashMap<String,Double> wordsProbably条件概率
	 * 输出:args[5],输出每一份文档经贝叶斯分类后所对应的类,格式为<doc,class>
	 */
    public static class DocOfClassMap extends Mapper<Text, Text, Text, Text> {
        private Text newKey = new Text();
        private Text newValue = new Text();

        @Override
        protected void setup(Context context) throws IOException, InterruptedException {
            GetPriorProbably();
            GetConditionProbably();
        }

        @Override
        protected void map(Text key, Text value, Context context) throws IOException, InterruptedException {
            int index = key.toString().indexOf(":");
            String docID = key.toString().substring(index + 1);
            String[] words = value.toString().split(" ");
            String className;
            String class_words;
            double probability;
            //遍历所有的类别,计算doc在每个类别下的概率
            for (Map.Entry<String, Double> entry : classProbably.entrySet()) {
                className = entry.getKey();
                probability = Math.log(entry.getValue());
                //遍历文档单词,输出概率值probability
                for (String word : words) {
                    class_words = className + ":" + word;
                    if (wordsProbably.containsKey(class_words)) {
                        probability += Math.log(wordsProbably.get(class_words));//如果测试文档的单词在训练集中出现过，则直接加上之前计算的概率
                    } else {
                        probability += Math.log(wordsProbably.get(className));//如果测试文档中出现了新单词则加上之前计算新单词概率
                    }
                }
                newKey.set(docID);//新的键值的key为<文档名>
                newValue.set(className + ":" + probability);//新的键值的value为<类名:概率>,即<class:probability>
                context.write(newKey, newValue);
            }
        }
    }

    public static class DocOfClassReduce extends Reducer<Text, Text, Text, Text> {
        private Text newValue = new Text();

        @Override
        protected void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
            double probability = 0.0;
            String className = null;
            boolean flag = false;
            for (Text value : values) {
                int index = value.toString().indexOf(":");
                if (flag != true) {
                    className = value.toString().substring(0, index);
                    probability = Double.parseDouble(value.toString().substring(index + 1));
                    flag = true;
                } else {
                    if (Double.parseDouble(value.toString().substring(index + 1)) > probability) {
                        probability = Double.parseDouble(value.toString().substring(index + 1));
                        className = value.toString().substring(0, index);
                    }
                }
            }
            newValue.set(className);
            context.write(key, newValue);
        }
    }

    /* 程序的入口
     * @Param
     * outArgs[0]: 训练集路径, job1(Words Counts)的输入
     * outArgs[1]: 处理训练集得到各个类别中每个单词的数量, job1(Words Counts)的输出路径, job2(Class Total Words)的输入, job3(Diff Total Words)的输入
     * outArgs[2]: 处理job1的输出得到各个类别单词总量, job2(Class Total Words)的输出路径
     * outArgs[3]: 处理job1的输出得到不同的单词的记录, job3(Diff Total Words)的输出路径
     * outArgs[4]: 测试集路径, job4(Doc-Class)的输入
     * outArgs[5]: 对测试集处理得到测试集的文档贝叶斯分类, job4(Doc-Class)的输出路径
     */

    public static void main(String[] args) throws IOException{
        Configuration configuration = new Configuration();
        configuration.set("fs.default.name", "hdfs://localhost:9000");
        System.out.println(configuration.toString());
        String[] paths = {
                "hdfs://localhost:9000/user/hadoop/bayes/training",
                "hdfs://localhost:9000/user/hadoop/bayes/ClassWordsCounts",
                "hdfs://localhost:9000/user/hadoop/bayes/ClassTotalWords",
                "hdfs://localhost:9000/user/hadoop/bayes/DiffTotalWords",
                "hdfs://localhost:9000/user/hadoop/bayes/test",
                "hdfs://localhost:9000/user/hadoop/bayes/DocToClass"
        };
        outArgs = paths;

        FileSystem hdfs = FileSystem.get(configuration);

        Path path1 = new Path(outArgs[1]);
        if (hdfs.exists(path1))
            hdfs.delete(path1, true);
        Job job1 = new Job(configuration, "WordsCounts");
        job1.setJarByClass(NaiveBayes.class);
        //设置Job序列化输入输出的格式
        job1.setInputFormatClass(SequenceFileInputFormat.class);
        job1.setOutputFormatClass(SequenceFileOutputFormat.class);
        //设置Map的入口类,以及输出的key和value的类型
        job1.setMapperClass(ClassWordCountsMap.class);
        job1.setMapOutputKeyClass(Text.class);
        job1.setMapOutputValueClass(IntWritable.class);
        //设置Reduce的入口类,以及输出的key和value的类型
        job1.setReducerClass(ClassWordCountsReduce.class);
        job1.setOutputKeyClass(Text.class);
        job1.setOutputValueClass(IntWritable.class);
        //加入控制器
        ControlledJob controlledJob1 = new ControlledJob(configuration);
        controlledJob1.setJob(job1);
        //job1的输入输出路径
        FileInputFormat.addInputPath(job1, new Path(outArgs[0]));
        FileOutputFormat.setOutputPath(job1, path1);

        Path path2 = new Path(outArgs[2]);
        if (hdfs.exists(path2))
            hdfs.delete(path2, true);
        Job job2 = new Job(configuration, "ClassTotalWords");
        job2.setJarByClass(NaiveBayes.class);
        //设置Job序列化输入输出的格式
        job2.setInputFormatClass(SequenceFileInputFormat.class);
        job2.setOutputFormatClass(SequenceFileOutputFormat.class);
        //设置Map的入口类,以及输出的key和value的类型
        job2.setMapperClass(ClassTotalWordsMap.class);
        job2.setMapOutputKeyClass(Text.class);
        job2.setMapOutputValueClass(IntWritable.class);
        //设置Reduce的入口类,以及输出的key和value的类型
        job2.setReducerClass(ClassTotalWordsReduce.class);
        job2.setOutputKeyClass(Text.class);
        job2.setOutputValueClass(IntWritable.class);
        //加入控制器
        ControlledJob controlledJob2 = new ControlledJob(configuration);
        controlledJob2.setJob(job2);
        //job2的输入输出路径
        FileInputFormat.addInputPath(job2, new Path(outArgs[1] + "/part-r-00000"));
        FileOutputFormat.setOutputPath(job2, path2);

        Path path3 = new Path(outArgs[3]);
        if (hdfs.exists(path3))
            hdfs.delete(path3, true);
        Job job3 = new Job(configuration, "DiffTotalWords");
        job3.setJarByClass(NaiveBayes.class);
        //设置Job序列化输入输出的格式
        job3.setInputFormatClass(SequenceFileInputFormat.class);
        job3.setOutputFormatClass(SequenceFileOutputFormat.class);
        //设置Map的入口类,以及输出的key和value的类型
        job3.setMapperClass(DiffTotalWordsMap.class);
        job3.setMapOutputKeyClass(Text.class);
        job3.setMapOutputValueClass(IntWritable.class);
        //设置Reduce的入口类,以及输出的key和value的类型
        job3.setReducerClass(DiffTotalWordsReduce.class);
        job3.setOutputKeyClass(Text.class);
        job3.setOutputValueClass(IntWritable.class);
        //加入控制器
        ControlledJob controlledJob3 = new ControlledJob(configuration);
        controlledJob3.setJob(job3);
        //job3的输入输出路径
        FileInputFormat.addInputPath(job3, new Path(outArgs[1] + "/part-r-00000"));
        FileOutputFormat.setOutputPath(job3, path3);

        Path path4 = new Path(outArgs[5]);
        if (hdfs.exists(path4))
            hdfs.delete(path4, true);
        Job job4 = new Job(configuration, "Doc-Class");
        job4.setJarByClass(NaiveBayes.class);
        //设置Job序列化输入输出的格式
        job4.setInputFormatClass(SequenceFileInputFormat.class);
        job4.setOutputFormatClass(SequenceFileOutputFormat.class);
        //设置Map的入口类,以及输出的key和value的类型
        job4.setMapperClass(DocOfClassMap.class);
        job4.setMapOutputKeyClass(Text.class);
        job4.setMapOutputValueClass(Text.class);
        //设置Reduce的入口类,以及输出的key和value的类型
        job4.setReducerClass(DocOfClassReduce.class);
        job4.setOutputKeyClass(Text.class);
        job4.setOutputValueClass(Text.class);
        //加入控制器
        ControlledJob controlledJob4 = new ControlledJob(configuration);
        controlledJob4.setJob(job4);
        //job4的输入输出路径
        FileInputFormat.addInputPath(job4, new Path(outArgs[4]));
        FileOutputFormat.setOutputPath(job4, path4);

        //作业之间的依赖关系
        controlledJob2.addDependingJob(controlledJob1);
        controlledJob3.addDependingJob(controlledJob1);
        controlledJob4.addDependingJob(controlledJob3);
        controlledJob4.addDependingJob(controlledJob2);

        //主控制容器
        JobControl jobControl = new JobControl("NativeBayes");
        jobControl.addJob(controlledJob1);
        jobControl.addJob(controlledJob2);
        jobControl.addJob(controlledJob3);
        jobControl.addJob(controlledJob4);

        //用线程的方式启动Job
        Thread theController = new Thread(jobControl);
        theController.start();
        while (true) {
            if (jobControl.allFinished()) {
                System.out.println(jobControl.getSuccessfulJobList());
                jobControl.stop();
                break;
            }
        }
    }
}

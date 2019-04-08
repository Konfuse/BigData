package com.bayes;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IOUtils;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.util.ReflectionUtils;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.StringTokenizer;

/**
 * @Author: Konfuse
 * @Date: 19-3-22 下午7:36
 */
public class NaiveBayes {
    static String[] outArgs;

    public static IntWritable calcSum(Iterable<IntWritable> values) {
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
     * 输入:args[1],输入格式为<<class,word>,counts>
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
	 * 输入:对应第一个MapReduce的输出<<class,word>,counts>,第二个MapReduce的输出<class,totalWords>,第三个MapReduce的输出<class,diffTotalWords>
	 * 输出:得到HashMap<String,Double>,即<<类名:单词>,概率>
	 */
    private static HashMap<String, Double> wordsProbably
}

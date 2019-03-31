package com.bayes;

import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;

import java.io.IOException;
import java.util.StringTokenizer;

/**
 * @Author: Konfuse
 * @Date: 19-3-22 下午7:36
 */
public class NaiveBayes {
    public static class ClassWordCountsMap extends Mapper<Text, Text, Text, IntWritable> {
        private Text newKey = new Text();
        private final IntWritable one = new IntWritable(1);

        @Override
        protected void map(Text key, Text value, Context context) throws IOException, InterruptedException {
            super.map(key, value, context);
            int index = key.toString().indexOf(":");
            String cls = key.toString().substring(0, index);
            StringTokenizer stringTokenizer = new StringTokenizer(value.toString());
            while (stringTokenizer.hasMoreTokens()) {
                newKey.set(cls + ":" + stringTokenizer.nextToken());
                context.write(newKey, one);
            }
        }
    }

    public static class ClassWordCountsReduce extends Reducer<Text, IntWritable, Text, IntWritable> {
        private IntWritable result = new IntWritable();

        @Override
        protected void reduce(Text key, Iterable<IntWritable> values, Context context) throws IOException, InterruptedException {
            super.reduce(key, values, context);
            int sum = 0;
            for (IntWritable value: values) {
                sum += value.get();
            }
            result.set(sum);
            context.write(key, result);
        }
    }
}

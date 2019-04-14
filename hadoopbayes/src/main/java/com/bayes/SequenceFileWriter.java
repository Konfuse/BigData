package com.bayes;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IOUtils;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.Text;

import java.io.*;
import java.net.URI;

/**
 * @Author: Konfuse
 * @Date: 19-3-22 下午5:57
 */
public class SequenceFileWriter {
    private static String fileToString(File file) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(file));

        String line;
        String result = "";
        while ((line = reader.readLine()) != null) {
            if (line.matches("[a-zA-Z]+")) {
                result += line + " ";
            }
        }
        reader.close();
        return result;
    }

    public static void main(String[] args) throws IOException {
        String trainSet = "/home/konfuse/Documents/Bayes/test";
        File[] dirs = new File(trainSet).listFiles();

        String uri = "hdfs://localhost:9000/user/hadoop/bayes/test";
        Configuration conf = new Configuration();
        FileSystem fs = FileSystem.get(URI.create(uri), conf);
        Path path = new Path(uri);

        Text key = new Text();
        Text value = new Text();

        SequenceFile.Writer writer = null;
        try {
            writer = SequenceFile.createWriter(fs, conf, path, key.getClass(), value.getClass());

            for (File dir : dirs) {
                File[] files = dir.listFiles();
                for (File file : files) {
                    key.set(dir.getName() + ":" + file.getName());
                    value.set(fileToString(file));
                    writer.append(key, value);
                    System.out.println(key + "\t" + value);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            IOUtils.closeStream(writer);
        }
    }
}

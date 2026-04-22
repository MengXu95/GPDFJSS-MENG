package mengxu;

import yimei.jss.gp.GPRun;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class GPTrain {
    public static void main(String[] args) {
        boolean isTest = true;
        int maxTests = 1;
        boolean isDynamic = true;

        String workingDirectory = (new File("")).getAbsolutePath();

        double[] utilLevels = new double[] {0.85,0.95};
        String[] objectives = new String[] {"mean-flowtime","mean-tardiness","mean-weighted-tardiness"};
        for (double utilLevel: utilLevels) {
            for (String objective: objectives) {
                File file=new File(utilLevel+objective);
                if(!file.exists()){//如果文件夹不存在
                    file.mkdir();//创建文件夹
                }

                List<String> gpRunArgs = new ArrayList<>();
                //include path to params file
                gpRunArgs.add("-file");
                if (isDynamic) {
                    String contributionSelectionStrategy = "3-Clustering";
                    String bbSelectionStrategy = "top-1";

                    //Calling -jar GPJSS-FC.jar -file params/fcgp-simplegp-dynamic.params -p eval.problem.eval-model.sim-models.0.util-level=0.95 -p eval.problem.eval-model.objectives.0=max-flowtime -p contributionSelectionStrategy=2-Clustering -p bbSelectionStrategy=2-Clustering -p seed.0=13 -p stat.file=job.13.out.stat

                    gpRunArgs.add("./params/coevolutiongp-dynamic.params");
                    //gpRunArgs.add(workingDirectory+"/src/yimei/jss/algorithm/featureconstruction/fcgp-simplegp-dynamic.params");
//                    gpRunArgs.add(workingDirectory+"/src/main/java/mengxu/jss/gp/nichinggp.params");
//                    gpRunArgs.add(workingDirectory+"/params/nichinggp.params");
//                    gpRunArgs.add(workingDirectory+"/src/main/java/mengxu/jss/gp/coevolutiongp-dynamic.params");
                    //gpRunArgs.add(workingDirectory+"/src/yimei/jss/algorithm/coevolutiongp/coevolutiongp-dynamic.params");
                    //gpRunArgs.add(workingDirectory+"/src/yimei/jss/algorithm/simplegp/simplegp-dynamic.params");
                    gpRunArgs.add("-p");
                    gpRunArgs.add("eval.problem.eval-model.sim-models.0.util-level="+utilLevel);
                    gpRunArgs.add("-p");
                    gpRunArgs.add("eval.problem.eval-model.objectives.0="+objective);
                    gpRunArgs.add("-p");

                    for (int i = 0; i < 1 && i <= maxTests; ++i) {
                        gpRunArgs.add("seed.0="+String.valueOf(i));
                        gpRunArgs.add("-p");
                        gpRunArgs.add("stat.file=job."+String.valueOf(i)+".out.stat");
                        //convert list to array
                        GPRun.main(gpRunArgs.toArray(new String[0]));
                        //save the output file.
                        String original = "./---/job."+String.valueOf(i)+".out.stat";
                        String goal = "./"+file+"/job."+String.valueOf(i)+".out.stat";
                        copyFile(original,goal);
                        String original1 = "./---/job."+String.valueOf(i)+".time.csv";
                        String goal1 = "./"+file+"/job."+String.valueOf(i)+".time.csv";
                        copyFile(original1,goal1);
                        //now remove the seed, we will add new value in next loop
                        gpRunArgs = gpRunArgs.subList(0,gpRunArgs.size()-3);
                    }
                }
            }
        }
    }

    public static void copyFile(String oldPath, String newPath) {
        try {
            int bytesum = 0;
            int byteread = 0;
            File oldfile = new File(oldPath);
            if (oldfile.exists()) { //文件存在时
                InputStream inStream = new FileInputStream(oldPath); //读入原文件
                FileOutputStream fs = new FileOutputStream(newPath);
                byte[] buffer = new byte[1444];
                int length;
                while ( (byteread = inStream.read(buffer)) != -1) {
                    bytesum += byteread; //字节数 文件大小
                    System.out.println(bytesum);
                    fs.write(buffer, 0, byteread);
                }
                inStream.close();
            }
        }
        catch (Exception e) {
            System.out.println("复制单个文件操作出错");
            e.printStackTrace();
        }
    }

    public static void copyFolder(String oldPath, String newPath) {
        try {
            // 如果文件夹不存在，则建立新文件夹
            (new File(newPath)).mkdirs();
            //读取整个文件夹的内容到file字符串数组，下面设置一个游标i，不停地向下移开始读这个数组
            File filelist = new File(oldPath);
            String[] file = filelist.list();
            //要注意，这个temp仅仅是一个临时文件指针
            //整个程序并没有创建临时文件
            File temp = null;
            for (int i = 0; i < file.length; i++) {
                //如果oldPath以路径分隔符/或者\结尾，那么则oldPath/文件名就可以了
                //否则要自己oldPath后面补个路径分隔符再加文件名
                //谁知道你传递过来的参数是f:/a还是f:/a/啊？
                if (oldPath.endsWith(File.separator)) {
                    temp = new File(oldPath + file[i]);
                } else {
                    temp = new File(oldPath + File.separator + file[i]);
                }

                //如果游标遇到文件
                if (temp.isFile()) {
                    FileInputStream input = new FileInputStream(temp);
                    FileOutputStream output = new FileOutputStream(newPath
                            + "/" + "rename_" + (temp.getName()).toString());
                    byte[] bufferarray = new byte[1024 * 64];
                    int prereadlength;
                    while ((prereadlength = input.read(bufferarray)) != -1) {
                        output.write(bufferarray, 0, prereadlength);
                    }
                    output.flush();
                    output.close();
                    input.close();
                }
                //如果游标遇到文件夹
                if (temp.isDirectory()) {
                    copyFolder(oldPath + "/" + file[i], newPath + "/" + file[i]);
                }
            }
        } catch (Exception e) {
            System.out.println("复制整个文件夹内容操作出错");
        }
    }
}

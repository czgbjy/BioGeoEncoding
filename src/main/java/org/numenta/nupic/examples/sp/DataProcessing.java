package org.numenta.nupic.examples.sp;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;


public class DataProcessing {

	static int width=61;
	static int height=61;
	
	public static void main(String[] args)
	{
		//compareCodeSimilarity();
		drawLocationField();
	}
	/**
	 * 计算编码的的相似度
	 */
	public static void compareCodeSimilarity()
	{
		// TODO Auto-generated method stub
		//首先读取这个txt文档\
		String path="D:\\workspace\\codeResultnnnn.txt";
		try 
		{
			List<String> scanListPath = readFile02(path);
			ArrayList<Integer[]> vectorSeries=new ArrayList<Integer[]>();//数组序列
			for (int i = 0; i < scanListPath.size(); i++) //读取每一行数据
			{
				String dataString=scanListPath.get(i);///这个i可以转换为这个点的位置
				dataString=dataString.substring(1, dataString.length()-1);
				String[] dataStringList=dataString.split(",");
				Integer[] array=new Integer[dataStringList.length-1];
				for (int j = 0; j < dataStringList.length-1; j++) 
				{
					String numberValueString=dataStringList[j].trim();
					array[j]=Integer.valueOf(numberValueString);						
				}
				vectorSeries.add(array);
			}
			Integer[] vectorInitial=vectorSeries.get(1);
			for (int i = 0; i < vectorSeries.size(); i++)
			{
				Integer[] vectorCompare=vectorSeries.get(i);
				double cosineSimilarity=computeCosinSimilarity(vectorInitial,vectorCompare);	 
				System.out.print(cosineSimilarity+"\n");
			}
			
		} catch (Exception e) {
			// TODO: handle exception
		}
	}
	/**
	 * 计算Cosine相似度的函数
	 * @param vector1
	 * @param vector2
	 * @return
	 */
	public static double computeCosinSimilarity(Integer[] vector1,Integer[] vector2)
	{
	    double sum = 0.0;
        Double v1Len = 0.0;
        Double v2Len = 0.0;
        for (int i = 0; i < vector1.length; i++) {
            sum += vector1[i] * vector2[i];
            v1Len += vector1[i]*vector1[i];
        }
        for (int k = 0;k <vector2.length ;k++)
        {
            v2Len += vector2[k]*vector2[k];
        }
       return sum / (Math.sqrt(v1Len) * Math.sqrt(v2Len));//相似度
	}
	public static void drawLocationField()
	{
		//String path = "D:\\paper\\论文_具有空间方位感知能力的神经网络\\神经网络测试数据集\\第二次\\10万次训练的结果\\61乘61的测试数据-1000分的原始数据及结果数据\\resultSP.txt";
		//String path = "D:\\paper\\论文_具有空间方位感知能力的神经网络\\神经网络测试数据集\\第二次\\10万次训练的结果\\61乘61的测试数据-1000分的原始数据及结果数据\\抑制区最大激活单元柱为5个双层的设置\\resultSP.txt";
		//String path = "D:\\paper\\论文_具有空间方位感知能力的神经网络\\神经网络测试数据集\\第二次\\10万次训练的结果\\61乘61的测试数据-1000分的原始数据及结果数据\\抑制区最大激活单元柱为5个的设置\\resultSP.txt";
		
		//String path = "D:\\paper\\论文_具有空间方位感知能力的神经网络\\神经网络测试数据集\\第二次\\10万次训练的结果\\61乘122的测试数据-1150分的原始数据及结果数据\\resultSP.txt";
		String path = "D:\\workspace\\resultSP61.txt";
		
        try {
			List<String> scanListPath = readFile02(path);
			ArrayList<Integer[]> cordinateSeries=new ArrayList<Integer[]>();
			
			for (int i = 0; i < scanListPath.size(); i++) //读取每一行数据
			{
				String dataString=scanListPath.get(i);///这个i可以转换为这个点的位置
				dataString=dataString.substring(1, dataString.length()-1);
				String[] dataStringList=dataString.split(",");
				for (int j = 0; j < dataStringList.length; j++) {
					String[] indexAndValue=dataStringList[j].split(":");
					if (indexAndValue[0].trim().equals("1")) 
					{
					Integer[] cordinates=computeCoordinates(i,height);
					String valueString=indexAndValue[1].trim();
					cordinates[2]=Double.valueOf(valueString).intValue();
					cordinateSeries.add(cordinates);
					if(cordinates[0]==16&&cordinates[1]==63)
					{
						System.out.print(0);
					}
					}
				}
			}
			new DrawSee(cordinateSeries,width,height);
		} 
        catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	/**
	 * 计算指定序号的计算结果的坐标
	 * @param index 相当于把1维转变为2维
	 * @return 一个坐标数组
	 */
    public static Integer[] computeCoordinates(int index, int dimension) {
    	Integer[] returnVal = new Integer[3];///前两个存储x和y坐标，最后一个存储激活值吧
    	Integer base = index;
        int quotient = base / dimension;
        base %= dimension;
        returnVal[0] = quotient;
        returnVal[1]=base;
        
        return returnVal;
    }
	
	
	public static List<String> readFile02(String path) throws IOException {
        // 使用一个字符串集合来存储文本中的路径 ，也可用String []数组
        List<String> list = new ArrayList<String>();
        FileInputStream fis = new FileInputStream(path);
        // 防止路径乱码   如果utf-8 乱码  改GBK     eclipse里创建的txt  用UTF-8，在电脑上自己创建的txt  用GBK
        InputStreamReader isr = new InputStreamReader(fis, "UTF-8");
        BufferedReader br = new BufferedReader(isr);
        String line = "";
        while ((line = br.readLine()) != null) {
            // 如果 t x t文件里的路径 不包含---字符串       这里是对里面的内容进行一个筛选
            if (line.lastIndexOf("---") < 0) {
                list.add(line);
            }
        }
        br.close();
        isr.close();
        fis.close();
        return list;
    }

}

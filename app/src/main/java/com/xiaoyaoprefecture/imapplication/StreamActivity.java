package com.xiaoyaoprefecture.imapplication;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class StreamActivity extends AppCompatActivity {
    @BindView(R.id.mBtnStart)
    Button mBtnStart;
    @BindView(R.id.mTvLog)
    TextView mTvLog;
    //volatile 保证多线程内存同步，避免出问题
    private volatile boolean mIsRecorder;//录音状态
    private ExecutorService mExecutorService;
    private Handler mMainThreadHandler;
    private File mAudioFile;
    private AudioRecord mAudioRecord;
    private FileOutputStream mFileOutputStream;
    private long startRecord,stopRecord;
    //buffer 不能太大，避免oom
    private static final int BUFFER_SIZE=2048;
    private byte []mBuffer;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stream);
        ButterKnife.bind(this);
        mBuffer=new byte[BUFFER_SIZE];
        //录音jni函数不具备线程安全，所以使用单线程
        mExecutorService= Executors.newSingleThreadExecutor();
        mMainThreadHandler= new Handler(Looper.getMainLooper());
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        //activity销毁时，停止后台任务，避免内存泄漏
        mExecutorService.shutdownNow();
    }

    //开始按钮的点击事件
    @OnClick(R.id.mBtnStart)
    public void start(){
        //根据当前状态改变UI，执行响应的逻辑
        if (mIsRecorder){//正在录音
            //改变UI状态
            mBtnStart.setText(R.string.start);
            //停止录音
            //改变录音状态
            mIsRecorder=false;
        }else{//没有录音
            //改变UI状态
            mBtnStart.setText(R.string.stop);
            Toast.makeText(StreamActivity.this,"请开始说话",Toast.LENGTH_SHORT).show();
            //开始录音
            //改变录音状态
            mIsRecorder=true;
            //提交后台任务，执行开始逻辑
            mExecutorService.submit(new Runnable() {
                @Override
                public void run() {
               //执行开始录音，失败就要提示用户
                    if (!startRecorder())
                    recordFail();
                }
            });
        }

    }

    /**
     * 录音失败的操作
     */
    private void recordFail() {
        mAudioFile=null;
        //Toast提示失败,因为Toast,所以需要在主线程执行
        mMainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(StreamActivity.this,"录音失败",Toast.LENGTH_SHORT).show();
                //重置录音状态和UI状态
                mIsRecorder=false;
                mBtnStart.setText(R.string.start);
            }
        });
    }

    /**
     * 启动录音逻辑
     * @return
     */
    private boolean startRecorder() {
        try {
            //创建一个录音的文件
            mAudioFile=new File(Environment.getExternalStorageDirectory().getAbsolutePath()
                    +"/XiaoYaoExercise"+System.currentTimeMillis()+".pcm");
            mAudioFile.getParentFile().mkdirs();
            mAudioFile.createNewFile();
            //创建文件输出流
            mFileOutputStream=new FileOutputStream(mAudioFile);
            //配置AudioRecord
            int audioSource= MediaRecorder.AudioSource.MIC;
            int sampleRate=44100;
            int channelConfig= AudioFormat.CHANNEL_IN_MONO;
            int audioFormat=AudioFormat.ENCODING_PCM_16BIT;
            int minBufferSize=AudioRecord.getMinBufferSize(sampleRate,channelConfig,audioFormat);
            //buffer不能小于最低要求，也不能小于我们每次读取的大小
            mAudioRecord=new AudioRecord(audioSource,sampleRate,channelConfig,audioFormat,
                    Math.max(minBufferSize,BUFFER_SIZE));
            //开始录音
            mAudioRecord.startRecording();
            //记录开始的录音时间，便于统计时长
            startRecord=System.currentTimeMillis();
            //循环读取数据，写到输出流里面
            while(mIsRecorder){
                int read=mAudioRecord.read(mBuffer,0,BUFFER_SIZE);
                if (read>0){//读取成功
                    //读取成功就要写入文件
                    mFileOutputStream.write(mBuffer,0,read);
                }else {//读取失败
                    return false;
                }
            }
            //退出循环,停止录音，释放录音资源
            return stopRecord1();

        } catch (IOException|RuntimeException e) {
            e.printStackTrace();
            //捕获异常，避免闪退，返回false提示用户
            return false;
        }finally {
            //释放AudioRecord
            if (mAudioRecord!=null){
                mAudioRecord.release();
            }
        }
    }

    /**
     * 结束录音逻辑
     * @return
     */
    private boolean stopRecord1() {

        try {
            //停止录音，关闭文件输入流
            mAudioRecord.stop();
            mAudioRecord.release();
            mAudioRecord=null;
            mFileOutputStream.close();
            //记录结束时间
            stopRecord=System.currentTimeMillis();
            //大于3秒，在主线程改变Ui
            final  int second= (int) ((stopRecord-startRecord)/1000);
            if (second>3){
                mMainThreadHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mTvLog.setText(mTvLog.getText()+"\n录音成功"+second+"秒");
                    }
                });
            }else {

            }
        } catch (IOException e) {
            e.printStackTrace();
            //捕获异常，避免闪退，返回false提示用户
            return false;
        }

        return true;
    }
}

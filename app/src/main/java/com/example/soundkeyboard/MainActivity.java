package com.example.soundkeyboard;


import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.TextView;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;

import jfftpack.RealDoubleFFT;


public class MainActivity extends AppCompatActivity {
    private boolean hasmic = false, isRecording, haswrite = false, hasread = false;
    Button btn_media_start, btn_media_stop, btn_media_play,btn_audio_record,btn_audio_stop,btn_audio_play;
    ImageView imageView;
    TextView txt_out;
    Bitmap bitmap;
    Canvas canvas;
    Paint paint;
    private String tmpfile;
    private final static String TAG = "MyTag";
    int cnt = 0;
    private MediaRecorder recorder;
    private MediaPlayer player;
    int media_strokes=0;
    int media_avg=0;
    //////
    int quite_avg=90;//note:暫時用強行設定 等開始寫預先訓練步驟時要求使用者安靜5秒來測定背景音量
    int stroke_power_max=500;//note:暫時用強行設定 之後寫預先訓練步驟時測定按鍵按下強度 用以壓制比按鍵大的聲音
    int stroke_power_min=150;//note:暫時用強行設定 之後寫預先訓練步驟時測定按鍵按下強度 用以偵測按鍵發生的最下限
    //note:之後測試標準差以及變異數
    /////
    //double media_avg2=0;
    //int media_total=0;
    File RF;
    File file;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate:123");
        setContentView(R.layout.activity_main);
        checkPermission();
        btn_media_start = findViewById(R.id.btn_media_record);
        btn_media_stop = findViewById(R.id.btn_media_end);
        btn_media_play = findViewById(R.id.btn_media_play);
        btn_audio_record=findViewById(R.id.btn_audio_record);
        btn_audio_stop=findViewById(R.id.btn_audio_end);
        btn_audio_play=findViewById(R.id.btn_audio_play);
        txt_out = findViewById(R.id.txt_out);
        btn_media_start.setOnClickListener(v -> startMediaRecording());
        btn_media_stop.setOnClickListener(v -> endMediaRecording());
        btn_media_play.setOnClickListener(v -> play());
        btn_audio_record.setOnClickListener(v->onclick_audio_start());
        btn_audio_stop.setOnClickListener(v->onclick_audio_stop());
        btn_audio_play.setOnClickListener(v->onclick_audio_play());
        imageView = (ImageView) this.findViewById(R.id.ImageView01);
        tmpfile = getExternalCacheDir().getAbsolutePath();
        Log.i(TAG,"pid= "+Thread.currentThread().getId());
        tmpfile += "/audiorecordtest.3gp";
        Log.i(TAG, "file path= " + tmpfile);

    }

    private void endMediaRecording() {
        if (!hasmic || !isRecording) return;
        handlerMeasure.removeCallbacks(taskMeasure);
        try {
            recorder.stop();
            recorder.release();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
        isRecording = false;
    }

    /**
     * 確認是否有麥克風使用權限
     */
    private void checkPermission() {
        hasmic = ActivityCompat.checkSelfPermission(this
                , Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        haswrite = ActivityCompat.checkSelfPermission(this
                , Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        hasread = ActivityCompat.checkSelfPermission(this
                , Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!hasmic) {
                this.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1);
            }
            if (!haswrite) {
                this.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 2);
            }
            if (!hasread) {
                this.requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 3);
            }
            if(hasread)
            {
                Log.i(TAG,"has read");
            }
        }

    }

    /**取得權限回傳*/
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            hasmic = true;
            Log.i(TAG, "get mic");
        }
        if (requestCode == 2 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            haswrite = true;
            Log.i(TAG, "get write");
        }
        if (requestCode == 3 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            hasread = true;
            Log.i(TAG, "get read");
        }
        if (!(grantResults.length > 0)) {
            Log.i(TAG, "request error");
        }
    }

    private void startMediaRecording() {
        if (!hasmic || isRecording) return;
        txt_out.setText("start");
        media_strokes=0;
        cnt=0;
        recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        /*
        try {
            RF = File.createTempFile("raw", ".amr", Environment.getExternalStorageDirectory());
            Log.i(TAG, "6969path = " +

                    RF.getAbsolutePath());


        } catch (IOException e) {
            e.printStackTrace();
        }
        */
        try {
            recorder.setOutputFile(tmpfile);
            recorder.prepare();
            recorder.start();
            isRecording = true;
            handlerMeasure.post(taskMeasure);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @SuppressLint("HandlerLeak")
    private Handler handlerMeasure = new Handler() {
        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case 1:
                    int amp = recorder.getMaxAmplitude();
                    //公式：Gdb = 20log10(V1/V0)
                    //Google已提供方法幫你取得麥克風的檢測電壓(V1)以及參考電壓(V0)
                    double db = 20 * (Math.log10(Math.abs(amp)));
                    cnt++;
                    //media_total+=amp;
                    //media_avg2=media_total/cnt;
                    if(cnt==1) media_avg=amp;
                    else{
                        //media_avg=(media_avg*(cnt-1)+amp)/cnt;
                        media_avg+=(amp-media_avg)/cnt;
                    }
                    if((double)amp>1.25*(double)media_avg) media_strokes++;

                    //if -Infinity
                    if (Math.round(db) == -9223372036854775808.0) txt_out.setText("0 db");
                    else txt_out.setText(amp + "amp, avg=" +media_avg+"strokes"+media_strokes);
                    //else txt_out.setText(Math.round(db) + " db, " + cnt);
                    //Log.i(TAG, "handler thread id = " +

                            //Thread.currentThread().getId());
                    break;
            }
            super.handleMessage(msg);

        }
    };
    private Runnable taskMeasure = new Runnable() {
        @Override
        public void run() {
            handlerMeasure.sendEmptyMessage(1);
            //每100毫秒抓取一次檢測結果
            handlerMeasure.postDelayed(this, 100);
            //Log.i(TAG, "workRunnable thread id = " +

            //        Thread.currentThread().getId());
        }
    };

    @Override
    protected void onStop() {
        super.onStop();
        endMediaRecording();
    }

    private void play() {
        player = new MediaPlayer();
        try {
            player.setDataSource(tmpfile);
            player.prepare();
            player.start();
        } catch (IOException e) {
            Log.e(TAG, "prepare() failed");
        }
        player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer MP) {
                MP.stop();
                MP.release();
            }
        });

    }

    //開始錄音
    public void StartAudioRecord() {
        Log.i(TAG, "開始錄音");
        double audio_avg_global=0;
        int audio_cnt_global=0;
        double pos_avg_global=0;
        double neg_avg_global=0;
        int pos_total_global=0;
        int pos_cnt_global=0;
        int stroke_cnt=0;
        int stroke_state=0;
        //for fft
        RealDoubleFFT transformer;
        //for drawing;
        bitmap = Bitmap.createBitmap((int)256,(int)100,Bitmap.Config.ARGB_8888);
        canvas = new Canvas(bitmap);
        paint= new Paint();
        paint.setColor(Color.GREEN);

//16K採集率
        int frequency = 44100;
//格式
        int channelConfiguration = AudioFormat.CHANNEL_IN_MONO;
//16Bit
        int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;
//生成PCM檔案
        file = new File(getExternalCacheDir().getAbsolutePath()+"/reverseme.pcm");
        Log.i(TAG, "生成檔案"+file.getAbsolutePath());
//如果存在，就先刪除再建立
        if(!file.exists()){
            Log.i(TAG,"檔案不存在");
        }
        if (file.exists()) {
            file.delete();
            Log.i(TAG, "刪除檔案");

            try {
                file.createNewFile();
                Log.i(TAG, "建立檔案");
            } catch (IOException e) {
                e.printStackTrace();
                Log.i(TAG, "未能建立");
                //throw new IllegalStateException("未能建立"+file.toString());
            }
        }
        try {//輸出流
            OutputStream os = new FileOutputStream(file);
            BufferedOutputStream bos = new BufferedOutputStream(os);
            DataOutputStream dos = new DataOutputStream(bos);
            //int bufferSize = AudioRecord.getMinBufferSize(frequency, channelConfiguration, audioEncoding);
            int bufferSize=4096;// set as power of 2 for fft
            transformer = new RealDoubleFFT(bufferSize);
            Log.i(TAG,"buffer size="+bufferSize);
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                checkPermission();
            }
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                checkPermission();
            }
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                checkPermission();
            }

            AudioRecord audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, frequency, channelConfiguration, audioEncoding, bufferSize);
            short[] buffer = new short[bufferSize];
            double[] toTransform = new double[bufferSize];
            audioRecord.startRecording();
            Log.i(TAG, "開始錄音");
            isRecording = true;
            int looptimes=0;
            while (isRecording) {
                //Log.i(TAG, "is recording");
                int audio_total=0;
                looptimes++;
                int bufferReadResult = audioRecord.read(buffer, 0, bufferSize);
                int localmin=0x3f3f3f3f;
                int localmax=-0x3f3f3f3f;
                int localzeros=0;
                int zerocross=0;
                int ispos=0;
                int lastpos=0;
                double audio_avg_local=0;
                int audio_cnt_local=0;
                int pos_total_local=0;
                int pos_cnt_local=0;
                int neg_total_local=0;
                double pos_avg_local=0;
                double neg_avg_local=0;
                //Log.i(TAG, "read result"+bufferReadResult);
                //every 0.08s
                for (int i = 0; i < bufferReadResult; i++  ) {
                    //double db = 20 * (Math.log10(Math.abs(buffer[i])));

                    if(looptimes==5) Log.i("special","i="+i+"buffer="+buffer[i]);


                    lastpos=ispos;

                    if(buffer[i]>0) {ispos=1;pos_total_local+=buffer[i];pos_cnt_local++;pos_cnt_global++;pos_total_global+=buffer[i];}
                    if(buffer[i]==0) ispos=ispos;
                    if(buffer[i]<0) {ispos=-1;neg_total_local+=buffer[i];}

                    if(lastpos!=ispos) zerocross++;

                    audio_cnt_global++;
                    audio_cnt_local++;
                    audio_avg_global+=(buffer[i]-audio_avg_global)/audio_cnt_global;
                    audio_avg_local+=(buffer[i]-audio_avg_local)/audio_cnt_local;
                    audio_total+=buffer[i];
                    if(buffer[i]==0) localzeros++;
                    if(localmax<buffer[i]) localmax=buffer[i];
                    if(localmin>buffer[i]) localmin=buffer[i];
                    //if(i%200==0)Log.i(TAG,"buffer["+i+"]content="+buffer[i]+"avg="+audio_avg);
                    dos.writeShort(buffer[i]);
                };
                pos_avg_local=pos_total_local/pos_cnt_local;
                pos_avg_global=pos_total_global/pos_cnt_global;
                neg_avg_local=neg_total_local/audio_cnt_local;

                if(stroke_state==0)
                {
                    if(pos_avg_local>=stroke_power_min&&pos_avg_local<stroke_power_max&&stroke_state>=0)
                    {
                        stroke_cnt++;
                        Log.i(TAG+"stroke detected","strokes= " +stroke_cnt+"looptimes="+looptimes);
                        stroke_state-=2;
                    }
                }
                else if(stroke_state==-1)
                {
                    stroke_state=0;
                }
                else if(stroke_state==-2)
                {
                    if(pos_avg_local<stroke_power_min)
                    {
                        stroke_state=0;
                    }
                    else stroke_state++;
                }
                /*
                if(pos_avg_local>=stroke_power_min&&pos_avg_local<stroke_power_max&&stroke_state>=0)
                {
                    stroke_cnt++;
                    Log.i(TAG+"stroke detected","strokes= " +stroke_cnt+"looptimes="+looptimes);
                    stroke_state-=2;
                }
                else
                {
                    detected_already=false;
                }
                */

                //Log.i(TAG,"looptimes="+looptimes+" max= "+localmax+"min= "+localmin);
                //Log.i(TAG,"looptimes"+looptimes+" loczlzeros="+localzeros+" zerocross= "+zerocross);
                Log.i(TAG,"looptimes="+looptimes+"pos avg local="+pos_avg_local);
            }
            audioRecord.stop();
            dos.close();
        } catch (Throwable t) {
            Log.e(TAG, "錄音失敗"+t);
        }
    }
    private void onclick_audio_start()
    {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                long idd=Thread.currentThread().getId();
                Log.i(TAG,"run audio start thread id="+idd);
                StartAudioRecord();
                Log.e(TAG,"start");
            }
        });
        thread.start();
        long iddd=Thread.currentThread().getId();
        Log.i(TAG,"audio start thread id="+iddd);
        ButtonEnabled(false,true,false);
    }
    private void ButtonEnabled(boolean start, boolean stop, boolean play) {
        btn_audio_record.setEnabled(start);
        btn_audio_stop.setEnabled(stop);
        btn_audio_play.setEnabled(play);
    }
    private void onclick_audio_stop()
    {
        isRecording = false;
        ButtonEnabled(true, false, true);
        Log.i(TAG,"audio stop");
    }

    private void onclick_audio_play()
    {
        if(file == null){
            Log.i(TAG,"Null file");
            return;
        }
//讀取檔案
        int musicLength = (int) (file.length() / 2);
        Log.i(TAG,"musiclen="+musicLength);
        short[] music = new short[musicLength];
        try {
            InputStream is = new FileInputStream(file);
            BufferedInputStream bis = new BufferedInputStream(is);
            DataInputStream dis = new DataInputStream(bis);
            int i = 0;
            while (dis.available() > 0) {
                music[i] = dis.readShort();
                if(i%200==0){
                    Log.d(TAG,"music["+i+"]="+music[i]);
                }
                i++;
            }
            dis.close();
            AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                    44100, AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    musicLength * 2,
                    AudioTrack.MODE_STREAM);
            audioTrack.play();
            audioTrack.write(music, 0, musicLength);
            audioTrack.stop();
            Log.i(TAG,"成功播放");
        } catch (Throwable t) {
            Log.e(TAG, "播放失敗"+t);
            t.printStackTrace();
        }
    }



}

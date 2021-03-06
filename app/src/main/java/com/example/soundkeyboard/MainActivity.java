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
    Button btn_media_start, btn_media_stop, btn_media_play,btn_audio_record,btn_audio_stop,btn_audio_play,btn_toggle_window;
    ImageView imageView;
    TextView txt_out;
    Bitmap bitmap;
    Canvas canvas;
    Paint paint;
    private String tmpfile;
    private final static String TAG = "MyTag";
    int cnt = 0;
    private MediaRecorder recorder;//?????????
    private MediaPlayer player;//?????????
    double[] toTransform;//??????????????????fft???data
    double most_freq=0.0;//fft?????????????????????
    int media_strokes=0;
    int media_avg=0;
    //////
    int quite_avg=90;//note:????????????????????? ??????????????????????????????????????????????????????5????????????????????????
    int stroke_power_max=500;//note:????????????????????? ?????????????????????????????????????????????????????? ?????????????????????????????????
    int stroke_power_min=150;//note:????????????????????? ?????????????????????????????????????????????????????? ????????????????????????????????????
    //note:????????????????????????????????????
    /////
    //double media_avg2=0;
    //int media_total=0;
    File RF;
    File file;
    int dowindow=1;//????????????window func 1?????? 0??????


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
        btn_toggle_window=findViewById(R.id.btn_toggle_window);
        txt_out = findViewById(R.id.txt_out);
        btn_media_start.setOnClickListener(v -> startMediaRecording());
        btn_media_stop.setOnClickListener(v -> endMediaRecording());
        btn_media_play.setOnClickListener(v -> play());
        btn_audio_record.setOnClickListener(v->onclick_audio_start());
        btn_audio_stop.setOnClickListener(v->onclick_audio_stop());
        btn_audio_play.setOnClickListener(v->onclick_audio_play());
        btn_toggle_window.setOnClickListener(v->toggle());
        bitmap = Bitmap.createBitmap((int)4096,(int)1000,Bitmap.Config.ARGB_8888);
        canvas = new Canvas(bitmap);
        paint= new Paint();
        paint.setColor(Color.GREEN);
        imageView = (ImageView) this.findViewById(R.id.ImageView01);
        imageView.setImageBitmap(bitmap);
        tmpfile = getExternalCacheDir().getAbsolutePath();
        Log.i(TAG,"pid= "+Thread.currentThread().getId());
        //tmpfile += "/audiorecordtest.3gp";
        //Log.i(TAG, "file path= " + tmpfile);
        imageView.invalidate();

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
     * ????????????????????????????????????
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

    /**??????????????????*/
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
                    //?????????Gdb = 20log10(V1/V0)
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
                case 2:
                    //for drawing;
                    double tmp[]= (double[]) msg.obj;
                    canvas.drawColor(Color.BLACK);
                    for (int i = 0; i < tmp.length; i++) {
                        int x = i;

                        int downy = (int) (1000 - (tmp[i] * 100));
                        int upy = 1000;

                        canvas.drawLine(x, downy, x, upy, paint);
                    }
                        imageView.invalidate();

                    break;

            }
            super.handleMessage(msg);

        }
    };
    private Runnable taskMeasure = new Runnable() {
        @Override
        public void run() {
            handlerMeasure.sendEmptyMessage(1);
            //???100??????????????????????????????
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

    private void play() {//not using
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

    //????????????
    public void StartAudioRecord() {
        Log.i(TAG, "????????????");
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

//16K?????????
        int frequency = 44100;
//??????
        int channelConfiguration = AudioFormat.CHANNEL_IN_MONO;
//16Bit
        int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;
//??????PCM??????
        file = new File(getExternalCacheDir().getAbsolutePath()+"/reverseme.pcm");
        Log.i(TAG, "????????????"+file.getAbsolutePath());
//????????????????????????????????????
        if(!file.exists()){
            Log.i(TAG,"???????????????");
        }
        if (file.exists()) {
            file.delete();
            Log.i(TAG, "????????????");

            try {
                file.createNewFile();
                Log.i(TAG, "????????????");
            } catch (IOException e) {
                e.printStackTrace();
                Log.i(TAG, "????????????");
                //throw new IllegalStateException("????????????"+file.toString());
            }
        }
        try {//?????????
            OutputStream os = new FileOutputStream(file);
            BufferedOutputStream bos = new BufferedOutputStream(os);
            DataOutputStream dos = new DataOutputStream(bos);
            //int bufferSize = AudioRecord.getMinBufferSize(frequency, channelConfiguration, audioEncoding);
            int bufferSize=4096;// set as power of 2 for fft
            int fftSize=bufferSize*2;
            transformer = new RealDoubleFFT(bufferSize*2);//!
            //Log.i(TAG,"buffer size="+bufferSize);
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
            toTransform = new double[bufferSize*2];//!
            //to calculate data after fft
            double[] re;
            double[] im;
            double[] magnitude;

            audioRecord.startRecording();
            Log.i(TAG, "????????????");
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
                for(int i=0;i<bufferSize*2;i++)
                {
                    toTransform[i]=0;
                }
                //read data from mic and predict strokes
                for (int i = 0; i < bufferReadResult; i++  ) {
                    //double db = 20 * (Math.log10(Math.abs(buffer[i])));
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

                    if(i==2000) Log.i(TAG,"buffreadreult="+bufferReadResult+"i= "+i+" buffer[i]="+buffer[i]);
                    /*
                    put data into fft array !important!
                     */
                    toTransform[i] = (double) buffer[i] / 32768.0;
                };
                pos_avg_local=pos_total_local/pos_cnt_local;
                pos_avg_global=pos_total_global/pos_cnt_global;
                neg_avg_local=neg_total_local/audio_cnt_local;
                /*
                apply window func before fft
                */
                if(dowindow>0) {
                    double window[] = new double[bufferSize * 2];
                    window = hanning(bufferSize * 2);
                    toTransform = applyWindowFunc(toTransform, window);
                }
                /*
                do fft
                 */
                transformer.ft(toTransform);

                /*
                do cut off frequency cut off frequency lower than lower and bigger than upeer
                 */
                int lower=3000;
                int upper=19000;
                toTransform=to_transform_cut_frequency(toTransform,lower,upper,frequency,fftSize);

                /*

                 */
                double[] spectrum;
                //to calculate spectrum
                if (toTransform.length % 2 != 0) {// if odd
                    spectrum = new double[(toTransform.length + 1) / 2];
                    re=new double[(toTransform.length + 1) / 2];
                    im=new double[(toTransform.length + 1) / 2];
                    magnitude=new double[(toTransform.length + 1) / 2];
                    re[0]=toTransform[0];//real part of first complex fft coeffient is x[0]
                    im[0]=0;
                    magnitude[0]=re[0]*re[0];
                    spectrum[0] = Math.pow(toTransform[0] * toTransform[0], 0.5);// dc component
                    for (int index = 1; index < toTransform.length; index = index + 2) {//i=1 3 5...
                        // magnitude =re*re + im*im
                        double mag = toTransform[index] * toTransform[index]
                                + toTransform[index + 1] * toTransform[index + 1];
                        re[(index + 1) / 2]=toTransform[index];//when index=1 3 5... i=1 2 3..
                        im[(index+1)/2]=toTransform[index+1];//index=2 4 6...i=1 2 3..
                        magnitude[(index+1)/2]=Math.sqrt(mag);
                        spectrum[(index + 1) / 2] = Math.pow(mag, 0.5);
                    }
                } else {// if even
                    spectrum = new double[toTransform.length / 2 + 1];
                    re=new double[toTransform.length / 2 + 1];
                    im=new double[toTransform.length / 2 + 1];
                    magnitude=new double[toTransform.length / 2 + 1];
                    re[0]=toTransform[0];//real part of first complex fft coeffient is x[0]
                    im[0]=0;
                    magnitude[0]=re[0]*re[0];
                    spectrum[0] = Math.pow(toTransform[0] * toTransform[0], 0.5);// dc component real only
                    for (int index = 1; index < toTransform.length - 1; index = index + 2) {//index=1 3 5.. i=1 2 3...
                        // magnitude =re*re + im*im
                        double mag = toTransform[index] * toTransform[index]
                                + toTransform[index + 1] * toTransform[index + 1];
                        re[(index + 1) / 2]=toTransform[index];//index=1 3 5..i=1 2 3
                        im[(index+1)/2]=toTransform[index+1];//index=2 4 6..i=1 2 3
                        magnitude[(index+1)/2]=Math.sqrt(mag);
                        spectrum[(index + 1) / 2] = Math.pow(mag, 0.5);
                    }
                    spectrum[spectrum.length - 1] = Math.pow(toTransform[toTransform.length - 1]
                            * toTransform[toTransform.length - 1], 0.5);
                    re[re.length - 1]=toTransform[toTransform.length - 1];
                    im[im.length-1]=0;
                    magnitude[magnitude.length-1]=Math.sqrt(re[re.length - 1]*re[re.length - 1]);
                }
                //after fft the real part is stored in index 1,3,5....(n/2)-1
                // Calculate the Real and imaginary and Magnitude.
                /*this is wrong
                for(int i = 0; i < bufferSize; i++){
                    // real is stored in first part of array
                    re[i] = toTransform[i*2];
                    // imaginary is stored in the sequential part
                    im[i] = toTransform[(i*2)+1];
                    // magnitude is calculated by the square root of (imaginary^2 + real^2)
                    magnitude[i] = Math.sqrt((re[i] * re[i]) + (im[i]*im[i]));
                }
                */

                //do inverse fft
                transformer.bt(toTransform);
                //Log.i(TAG,"to trans form len after inv fft="+toTransform.length);
                int tmp_stat=0;
                /* debug
                for(int i=0;i<toTransform.length;i++)
                {
                    if(i==2000)Log.i(TAG,"coeff "+i+ " after inv fft="+toTransform[i]);
                    //if(i>4096&&toTransform[i]>0.5)Log.i(TAG,"coeff "+i+ " !!!this should not >1"+toTransform[i]);
                }
                */

                /*
                trying to write transformed data into pcm file
                 */
                for(int i=0;i<bufferSize;i++)
                {
                    short tmp=(short)Math.round(toTransform[i]);
                    dos.writeShort(tmp);
                    if(i==2000)Log.i(TAG,"tmp "+i+ " tmp=="+tmp);
                }

                //end of do inverse fft
                double peak = -1.0;
                int peak_location=-1;
                int len=magnitude.length;
                // Get the largest magnitude peak
                for(int i = 0; i <len; i++){
                    if(peak < Math.abs(spectrum[i])) {
                        peak = Math.abs(spectrum[i]);
                        peak_location = i;
                    }
                }
                most_freq = (double)((double)frequency * (double)peak_location)/(double)(bufferSize*2);
                //Log.i(TAG,"Most freq="+most_freq);

                //three state counter if recently detected block for a moment to prevent error
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
                //cut_frequency(spectrum, 3000, 18000, frequency, fftSize);

                //to draw using handler by sending msg
                Message msg = handlerMeasure.obtainMessage();
                msg.what=2;
                msg.obj=spectrum;
                handlerMeasure.sendMessage(msg);

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


                //Log.i(TAG,"looptimes="+looptimes+" max= "+localmax+"min= "+localmin);
                //Log.i(TAG,"looptimes"+looptimes+" loczlzeros="+localzeros+" zerocross= "+zerocross);
                //Log.i(TAG,"looptimes="+looptimes+"pos avg local="+pos_avg_local);
                */

            }
            audioRecord.stop();
            dos.close();
        } catch (Throwable t) {
            Log.e(TAG, "????????????"+t);
            t.printStackTrace();
        }
    }
    private void onclick_audio_start()
    {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                long idd=Thread.currentThread().getId();
                //Log.i(TAG,"run audio start thread id="+idd);
                StartAudioRecord();
                //Log.e(TAG,"start");
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
//????????????
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
                    //Log.d(TAG,"music["+i+"]="+music[i]);
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
            Log.i(TAG,"????????????");
        } catch (Throwable t) {
            Log.e(TAG, "????????????"+t);
            t.printStackTrace();
        }
    }


    /**
     * apply desired window function to the signal for fft
     *
     * @param signal
     *            : the signal intended to apply the window on
     * @param window
     *            : window function
     * @return the resulting signal after being applied the window
     */
    private static double[] applyWindowFunc(double[] signal, double[] window) {
        double[] result = new double[signal.length];
        int leng= window.length;
        for (int i = 0; i < leng; i++) {
            result[i] = signal[i] * window[i];
        }
        return result;
    }

    /**
     * generate hanning window function
     *
     * @param windowSize
     * @return the hanning window of a size "windowSize"
     */
    private double[] hanning(int windowSize) {
        double h_wnd[] = new double[windowSize]; // Hanning window
        if(dowindow==1)//hanning
            for (int i = 0; i < windowSize; i++) { // calculate the hanning window
            h_wnd[i] = 0.5d * (1d - Math.cos(2.0 * Math.PI * i / (windowSize - 1)));
            //window[i] = 0.54f - 0.46f * cos( (float)i * 2.0f * ?? / (n-1) );
        }
        if(dowindow==2)//hamming
            for (int i = 0; i < windowSize; i++) { // calculate the hanning window
            h_wnd[i] = 0.54d - 0.46d*Math.cos(2.0 * Math.PI * i / (windowSize - 1));
            //window[i] = 0.54f - 0.46f * cos( (float)i * 2.0f * ?? / (n-1) );
        }
        return h_wnd;
    }

    private void toggle()
    {

        if(dowindow==2) {dowindow=0;txt_out.setText("window close"); return;}
        if(dowindow==1) {dowindow=2;txt_out.setText("window hamming v2"); return;}
        if(dowindow==0) {dowindow=1;txt_out.setText("window hanning v1");return;}

    }
    //given input spectrum set frequcncy between lower upper =0
    //most_freq = (double)((double)frequency * (double)peak_location)/(double)(bufferSize*2);
    //affect on only spectrum not fft data itself
    private double[] cut_frequency(double[] spectrum ,int lower_bound,int upper_bound,int frequency,int fftsize)
    {
        int len=spectrum.length;
        for(int i=0;i<len;i++)
        {
            double freq = (double)((double)frequency * (double)i)/(double)(fftsize);
            if(freq>=lower_bound-10&&freq<=upper_bound+10)
            {
                spectrum[i]=0;
            }
        }
        return spectrum;
    }
//use it after fft
//affect on fft data itself
    private double[] to_transform_cut_frequency(double[] toTransform ,int lower_bound,int upper_bound,int frequency,int fftsize) {
        int len = toTransform.length;


            for (int index = 1; index < len; index = index + 2)
            {//index=1 3 5... i=1 2 3(index+1)/2...
                int i = (index + 1) / 2;
                double freq = (double)((double)frequency * (double)i)/(double)(fftsize);
                if(freq>=lower_bound-10&&freq<=upper_bound+10)
                {
                    toTransform[index]=0;
                    toTransform[index+1]=0;
                }
            }
            return toTransform;
    }


//end of class
}

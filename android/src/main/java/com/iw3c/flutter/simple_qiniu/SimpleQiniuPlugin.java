package com.iw3c.flutter.simple_qiniu;

// 上传进度相关
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;

// 七牛sdk
import com.qiniu.android.common.AutoZone;
import com.qiniu.android.common.FixedZone;
import com.qiniu.android.common.Zone;
import com.qiniu.android.http.ResponseInfo;
import com.qiniu.android.storage.Configuration;
import com.qiniu.android.storage.UpCancellationSignal;
import com.qiniu.android.storage.UpCompletionHandler;
import com.qiniu.android.storage.UpProgressHandler;
import com.qiniu.android.storage.UploadManager;
import com.qiniu.android.storage.UploadOptions;

import org.json.JSONObject;

import android.util.Log;

import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.Registrar;

/** SimpleQiniuPlugin */
public class SimpleQiniuPlugin implements MethodCallHandler, EventChannel.StreamHandler {

  private boolean isCancelled;
  private static final String UploadProgressFilter = "qiniuUploadProgressFilter";
  private Registrar registrar;
  private BroadcastReceiver receiver;

  /** Plugin registration. */
  public static void registerWith(Registrar registrar) {
    SimpleQiniuPlugin plugin = new SimpleQiniuPlugin(registrar);
    final MethodChannel channel = new MethodChannel(registrar.messenger(), "simple_qiniu");
    channel.setMethodCallHandler(plugin);
    EventChannel eventChannel = new EventChannel(registrar.messenger(), "simple_qiniu_event");
    eventChannel.setStreamHandler(plugin);
  }

  private SimpleQiniuPlugin(Registrar registrar){
    this.registrar = registrar;
  }

  @Override
  public void onMethodCall(MethodCall call, Result result) {
    switch (call.method) {
      case "upload":
        onUpload(call, result);
        break;
      case "uploadData":
        onUploadData(call, result);
        break;
      case "cancelUpload":
        onCancelUpload(result);
        break;
      default:
        result.notImplemented();
        break;
    }
  }

  /**
   * 配置信息
   * @param zone
   * @return
   */
  private Configuration config(String zone){
    // 上传的配置信息
    Configuration config = new Configuration.Builder()
            .chunkSize(512 * 1024)        // 分片上传时，每片的大小。 默认256K
            .putThreshhold(1024 * 1024)   // 启用分片上传阀值。默认512K
            .connectTimeout(10)           // 链接超时。默认10秒
            .useHttps(true)               // 是否使用https上传域名
            .responseTimeout(60)          // 服务器响应超时。默认60秒
            .zone(getZone(zone))        // 设置区域，指定不同区域的上传域名、备用域名、备用IP。
            .build();
    return config;
  }

  /**
   * 上传完成处理
   * @param result
   * @return
   */
  private UpCompletionHandler completeHandler(final Result result){
    UpCompletionHandler upCompletionHandler = new UpCompletionHandler() {
      @Override
      public void complete(String key, ResponseInfo info, JSONObject res) {
        //res包含hash、key等信息，具体字段取决于上传策略的设置
        if(info.isOK()) {
          setLog("qiniu", "Upload Success");
        } else {
          setLog("qiniu", "Upload Fail: "+info.error);
          //如果失败，这里可以把info信息上报自己的服务器，便于后面分析上传错误原因
        }
        result.success(info.isOK());
        setLog("qiniu", key + ",\r\n " + info + ",\r\n " + res);
      }
    };
    return upCompletionHandler;
  }

  /**
   * 上传进度处理
   * @return
   */
  private UpProgressHandler progressHandler(){
    UpProgressHandler upProgressHandler = new UpProgressHandler(){
      public void progress(String key, double percent){
        setLog("qiniu", key + ": " + percent);
        Intent i = new Intent();
        i.setAction(UploadProgressFilter);
        i.putExtra("percent", percent);
        registrar.context().sendBroadcast(i);
      }
    };
    return upProgressHandler;
  }

  /**
   * 监听上传进度
   * @param obj
   * @param eventSink
   */
  @Override
  public void onListen(Object obj, final EventChannel.EventSink eventSink) {
    this.isCancelled = false;
    receiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        double percent = intent.getDoubleExtra("percent", 0);
        eventSink.success(percent);
      }
    };
    registrar.context().registerReceiver(receiver,new IntentFilter(UploadProgressFilter));
  }

  /**
   * 取消上传信号处理
   * @return
   */
  private UpCancellationSignal cancelSignalHandler(){
    UpCancellationSignal upCancellationSignal = new UpCancellationSignal(){
      public boolean isCancelled(){
        return isCancelled;
      }
    };
    return upCancellationSignal;
  }

  /**
   * 上传时的选项
   * @return
   */
  private UploadOptions optionHandler(){
    // 上传进度监听
    UpProgressHandler upProgressHandler = this.progressHandler();
    // 上传取消的信号监听
    UpCancellationSignal upCancellationSignal = this.cancelSignalHandler();
    UploadOptions uploadOptions = new UploadOptions(
            null,
            null,
            false,
            upProgressHandler,
            upCancellationSignal
    );
    return uploadOptions;
  }
  /**
   * 上传文件
   * @param call
   * @param result
   */
  private void onUpload(final MethodCall call, final Result result){
    this.isCancelled = false;
    // 文件路径
    final String filePath = call.argument("filePath");
    // 服务器中的key,如 2019/05/16/simple_qiniu.m4a, 必须唯一
    final String key = call.argument("key");
    // 上传所需的TOKEN，从服务器中获取
    final String token = call.argument("token");
    // 上传区域，如"华北机房"，默认为自动
    final String zone = call.argument("zone");

    // 重用uploadManager。一般地，只需要创建一个uploadManager对象
    UploadManager uploadManager = new UploadManager(this.config(zone));
    // 上传完成
    UpCompletionHandler upCompletionHandler = this.completeHandler(result);
    // 上传
    uploadManager.put(filePath, key, token, upCompletionHandler, optionHandler());
  }

  /**
   * 上传
   * @param call
   * @param result
   */
  private void onUploadData(final MethodCall call, final Result result){
    this.isCancelled = false;
    // 需要上传的数据
    byte[] data = call.argument("data");
    // 服务器中的key,如 2019/05/16/simple_qiniu.m4a, 必须唯一
    final String key = call.argument("key");
    // 上传所需的TOKEN，从服务器中获取
    final String token = call.argument("token");
    // 上传区域，如"华北机房"，默认为自动
    final String zone = call.argument("zone");

    // 重用uploadManager。一般地，只需要创建一个uploadManager对象
    UploadManager uploadManager = new UploadManager(this.config(zone));
    // 上传完成
    UpCompletionHandler upCompletionHandler = this.completeHandler(result);
    // 上传
    uploadManager.put(data, key, token, upCompletionHandler, optionHandler());
  }
  /**
   * 取消上传
   * @param result
   */
  private void onCancelUpload(final Result result){
    this.isCancelled = true;
    result.success(null);
  }
  @Override
  public void onCancel(Object o) {
    this.isCancelled = true;
    registrar.context().unregisterReceiver(receiver);
  }
  /**
   * 设置日志
   * @param tag
   * @param message
   */
  private void setLog(String tag, String message){
    Log.i(tag, message);
  }
  /**
   * 获取上传的区域
   * @param param
   * @return
   */
  private Zone getZone(String param) {
    Zone zone;
    switch (param) {
      case "0":
        zone = FixedZone.zone0;
        break;
      case "1":
        zone = FixedZone.zone1;
        break;
      case "2":
        zone = FixedZone.zone2;
        break;
      case "3":
        zone = FixedZone.zoneNa0;
        break;
      case "4":
        zone = FixedZone.zoneAs0;
        break;
      default:
        zone = AutoZone.autoZone;
        break;
    }

    return zone;
  }
}

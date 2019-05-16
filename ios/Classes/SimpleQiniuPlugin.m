#import "SimpleQiniuPlugin.h"
#import <QiniuSDK.h>
@interface SimpleQiniuPlugin() <FlutterStreamHandler>

@property BOOL isCanceled;
@property FlutterEventSink eventSink;

@implementation SimpleQiniuPlugin
+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
  FlutterMethodChannel* channel = [FlutterMethodChannel
      methodChannelWithName:@"simple_qiniu"
            binaryMessenger:[registrar messenger]];
  SimpleQiniuPlugin* instance = [[SimpleQiniuPlugin alloc] init];
  [registrar addMethodCallDelegate:instance channel:channel];
  FlutterEventChannel *eventChannel = [FlutterEventChannel eventChannelWithName:eventChannelName binaryMessenger:registrar.messenger];
    SimpleQiniuPlugin* instance = [[SimpleQiniuPlugin alloc] init];
    [registrar addMethodCallDelegate:instance channel:channel];
      [eventChannel setStreamHandler:instance];
}

- (void)handleMethodCall:(FlutterMethodCall*)call result:(FlutterResult)result {
  if ([@"onUpload" isEqualToString:call.method]){
    [self onUpload:call result:result];
  } else if ([@"onUploadData" isEqualToString:call.method]){
    [self onUploadData:call result:result];
  } else if ([@"onCancelUpload" isEqualToString:call.method]){
    [self onCancelUpload:call result:result];
  } else {
    result(FlutterMethodNotImplemented);
  }
    
}

- (void)upload:(FlutterMethodCall*)call result:(FlutterResult)result{
    self.isCanceled = FALSE;
    
    NSString *filePath = call.arguments[@"filePath"];
    NSString *key = call.arguments[@"key"];
    NSString *token = call.arguments[@"token"];
    
    QNUploadOption *opt = [[QNUploadOption alloc] initWithMime:nil progressHandler:^(NSString *key, float percent) {
        NSLog(@"progress %f",percent);
        self.eventSink(@(percent));
    } params:nil checkCrc:NO cancellationSignal:^BOOL{
        return self.isCanceled;
    }];
    
    QNUploadManager *manager = [[QNUploadManager alloc] init];
    [manager putFile:filePath key:key token:token complete:^(QNResponseInfo *info, NSString *key, NSDictionary *resp) {
        NSLog(@"info %@", info);
        NSLog(@"resp %@", resp);
        result(@(info.isOK));
    } option:(QNUploadOption *) opt];
}

- (void)uploadData:(FlutterMethodCall*)call result:(FlutterResult)result{
    self.isCanceled = FALSE;
    
    NSData *data = call.arguments[@"data"];
    NSString *key = call.arguments[@"key"];
    NSString *token = call.arguments[@"token"];
    NSString *zone = call.arguments[@"zone"];
    
    QNUploadOption *opt = [[QNUploadOption alloc] initWithMime:nil progressHandler:^(NSString *key, float percent) {
        NSLog(@"progress %f",percent);
        self.eventSink(@(percent));
    } params:nil checkCrc:NO cancellationSignal:^BOOL{
        return self.isCanceled;
    }];
    
    QNConfiguration *config = [QNConfiguration build:^(QNConfigurationBuilder *builder) {
        builder.zone = [self getZone:zone];
    }];
    
    QNUploadManager *manager = [[QNUploadManager alloc] initWithConfiguration:config];
    [manager putData:data key:key token:token complete:^(QNResponseInfo *info, NSString *key, NSDictionary *resp) {
        NSLog(@"info %@", info);
        NSLog(@"resp %@", resp);
        result(@(info.isOK));
    } option:(QNUploadOption *) opt];
}

- (void)onCancelUpload:(FlutterMethodCall*)call result:(FlutterResult)result{
    self.isCanceled = TRUE;
}

- (FlutterError * _Nullable)onCancelWithArguments:(id _Nullable)arguments {
    self.isCanceled = TRUE;
    self.eventSink = nil;
    return nil;
}

- (FlutterError * _Nullable)onListenWithArguments:(id _Nullable)arguments eventSink:(nonnull FlutterEventSink)events {
    self.isCanceled = FALSE;
    self.eventSink = events;
    return nil;
}

- (NSString) getZone: (NSString param) {
    QNFixedZone *zone;
    if([@"0" isEqualToString:param]){
        zone = [QNFixedZone zone0];
    }else if([@"1" isEqualToString:param]){
        zone = [QNFixedZone zone1];
    }else if([@"2" isEqualToString:param]){
        zone = [QNFixedZone zone2];
    }else if([@"3" isEqualToString:param]){
        zone = [QNFixedZone zoneNa0];
    }else if([@"4" isEqualToString:param]){
        zone = [QNFixedZone zoneAs0];
    }else{
        zone = [QNFixedZone autoZone];
    }
    return zone;
}

@end

//
//  IJKMoviePlayerController.h
//  IJKMediaDemo
//
//  Created by ZhangRui on 13-9-21.
//  Copyright (c) 2013年 bilibili. All rights reserved.
//

#import <UIKit/UIKit.h>
#import <IJKMediaPlayer/IJKMediaPlayer.h>
@class IJKMediaControl;

@interface IJKVideoViewController : UIViewController

@property(atomic, retain) id<IJKMediaPlayback> player;

- (void)setUrlString:(NSString *)URLString UrlSourceType:(IJKMPMovieSourceType) urlSourceType;

- (id)initView;

- (IBAction)onClickMediaControl:(id)sender;
- (IBAction)onClickOverlay:(id)sender;
- (IBAction)onClickBack:(id)sender;
- (IBAction)onClickPlay:(id)sender;
- (IBAction)onClickPause:(id)sender;
- (IBAction)onClickBackPlayREL:(id)sender;
- (IBAction)onClickBackPlayABS:(id)sender;
- (IBAction)onClickBackLive:(id)sender;
- (IBAction)onClickSlower:(id)sender;
- (IBAction)onClickFaster:(id)sender;
- (IBAction)onClickNormal:(id)sender;
- (IBAction)onClickSetVolume:(id)sender;
@property (weak, nonatomic) IBOutlet UITextField *volume;

@property(nonatomic,strong) IBOutlet IJKMediaControl *mediaControl;
@property(nonatomic,strong) NSString *urlString;

@property (nonatomic, strong) IBOutlet UITextField *relTimeTextField;
@property (nonatomic, strong) IBOutlet UITextField *startTimeTextField;
@property (nonatomic, strong) IBOutlet UITextField *endTimeTextField;

@end

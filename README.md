# ThermalEyes
双目热成像手机APP，用于获取摄像头数据和热成像数据，并进行融合显示

## Environment
Android Studio Bumblebee | 2021.1.1 Patch 2

## Test Platform
meizu 18s

## Depends on
 - [UVCAndroid](https://github.com/shiyinghan/UVCAndroid)
 - [UsbSerial](https://github.com/felHR85/UsbSerial)

## Structure
```
 |- app  // APP Demo, 冒烟基于该app
 |- app2 // 存在问题，未启用
```

## Feature
1. 获取热成像数据
2. 获取摄像头数据
3. 图像融合
4. 图像显示
5. 融合模式切换
6. 伪彩颜色空间切换
7. 算法参数调整（视差补偿、高频占比）
8. 高低温跟踪和显示
9. 显示下位机log
10. 保存用户配置(TODO)
11. 热成像帧率调整
12. 拍照(TODO)
13. 后期分析(TODO)
    
## Development
由于 Android 手机 USB 端口被占用，需要使用 WIFI 无线调试，开启方法如下：

1. 使用 USB 线将手机连接电脑，开启USB调试，在 Android Studio 中的 Terminal 窗口输入`adb devices`能看到设备
2. 保证电脑和手机在同一个局域网下，然后输入命令`adb tcpip 5555`开启手机远程调试功能
3. 找到手机的IP地址，执行命令`adb connect <IP>`进行远程连接
4. 最后断开手机USB连接，执行命令`adb devices`能看到设备既已经连接
    
## Change Log
20231128 
 - 完成基本功能

20231209
 - 集成OpenCV
 - 改用高斯模糊提取高频信息
 - 增加视差调整功能

20231221
 - 增加高低温跟踪
 - 增加菜单栏算法参数配置
 - 屏幕旋转锁定
 - 修改下位机通信协议

20230203
 - 增加热成像帧率调整选项
 - camera连接时打开热成像，删除打开关闭按钮
 - 优化软件界面
 - 适配3d外壳的视差调整
 - 修复第二次插入闪退问题

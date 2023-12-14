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
9. 热成像帧率调整(TODO)
10.拍照(TODO)
11.后期分析(TODO)
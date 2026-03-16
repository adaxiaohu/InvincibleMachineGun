@echo off
chcp 65001 >nul

:: 这里是模拟 CMD 的显示效果
:: 屏幕上会显示出指令，你只需要按回车即可
set /p="E:\meteoraddons开发\minecraft-fakeplayer-folia-master> gradlew clean build"

:: 当你按回车后，下面这行才会执行
gradlew clean build
:: 执行完后停留在控制台，不自动关闭
cmd /k
#!/bin/bash

# 启动 Nacos
echo "Starting Nacos..."
if [ -d "/Users/dundundebaba/Desktop/tool/nacos" ]; then
    (
      cd "/Users/dundundebaba/Desktop/tool/nacos" && bash ./start.sh
    )
else
    echo "Error: Nacos directory not found at /Users/dundundebaba/Desktop/tool/nacos"
fi

# 启动 Sentinel
echo "Starting Sentinel..."
if [ -d "/Users/dundundebaba/Desktop/tool/sentinel" ]; then
    (
      cd "/Users/dundundebaba/Desktop/tool/sentinel" && bash ./start.sh
    )
else
    echo "Error: Sentinel directory not found at /Users/dundundebaba/Desktop/tool/sentinel"
fi

echo "All tools started."

#!/bin/bash

max_runs=$1
echo "Running grading script " $max_runs "times";
echo "";

for i in `seq 1 $max_runs`
do
    echo "****************************************************************************";
    echo "Grading script run -" $i ": (" `date` ")";
    ./simpledynamo-grading.linux SimpleDynamo/app/build/outputs/apk/debug/app-debug.apk >> /home/linus/Desktop/PA4_Runs/Run_${i}_output.txt
    tail -1  /home/linus/Desktop/PA4_Runs/Run_${i}_output.txt
done

echo ""
echo $max_runs " runs of grading script completed : (" `date` ")";


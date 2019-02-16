#!/usr/bin/env python

import sys, os, time, platform

def main():
  if len(sys.argv) != 2:
    print "Please enter the number of AVDs you want to launch"
    print "Usage: " + sys.argv[0] + " <number of AVDs>"
    return

  head = "emulator"
  tail = "&"
  kvm = ""
  if platform.system() == "Windows":
    head = "start /B emulator"
    tail = ""
  elif platform.system() == "Linux":
    head = os.popen("which emulator").read().strip("\n")
    kvm = " -qemu -enable-kvm"
  for i in range(int(sys.argv[1])):
    n = str(i)
    cmd = head + " -avd avd" + n + kvm + tail
    print cmd
    os.system(cmd)
    time.sleep(2)

if __name__ == "__main__":
  main()

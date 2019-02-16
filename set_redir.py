#!/usr/bin/env python

import sys, os, tempfile

def main():
  if len(sys.argv) != 2:
    print "Please enter the listening port number of your app"
    print "Usage: " + sys.argv[0] + " <port>"
    return

  fd, name = tempfile.mkstemp()
  os.close(fd)

  os.system("adb devices > " + name)

  f = open(name, "r")
  for line in f:
    if "offline" in line:
      print "Some AVDs are offline. Please try again."
      f.close()
      os.remove(name)
      return
  f.seek(0)

  lport = sys.argv[1]
  cnt = 0
  for line in f:
    if not "emulator" in line:
      continue

    emu_name = line.split()[0].strip()
    port = str(int(emu_name.split("-")[1]) * 2)
    cmd = "adb -s " + emu_name + " forward tcp:" + port + " tcp:" + lport
    print cmd
    os.system(cmd)
    cnt = cnt + 1

  if cnt == 0:
    print "There is no AVD running"

  f.close()
  os.remove(name)

if __name__ == "__main__":
  main()

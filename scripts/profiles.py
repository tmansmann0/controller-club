#!/usr/bin/env python3
"""Add named Club presets without changing the active emulator configuration."""
import argparse
import os
import re
import subprocess
import xml.etree.ElementTree as ET

ADB = ["adb", "-s", os.environ["ANDROID_SERIAL"]] if os.environ.get("ANDROID_SERIAL") else ["adb", "-d"]
ROOT = "/sdcard/Android/data/"
DOLPHIN = ROOT + "org.dolphinemu.dolphinemu/files/Config/Profiles/"
CEMU = ROOT + "info.cemu.cemuplus/files/controllerProfiles/"

def shell(*args, data=None, check=True):
    return subprocess.run([*ADB, "shell", "-T", *args], input=data, text=True,
                          capture_output=True, check=check)

def install(path, data):
    # The shell's noclobber operation keeps existing files safe even on repeated runs.
    parent = path.rpartition("/")[0]
    result = shell(f"mkdir -p '{parent}' && (set -C; cat > '{path}')", data=data, check=False)
    if result.returncode == 0:
        print("Added", path.rpartition("/")[2])
    elif shell(f"test -f '{path}'", check=False).returncode == 0:
        print("Kept existing", path.rpartition("/")[2])
    else:
        raise RuntimeError(result.stderr.strip() or path)

def pads():
    dump = shell("dumpsys input").stdout
    pattern = r"(?m)^ +\d+: Controller Club P([1-4])\s*\n(?:(?!^ +\d+:|^Input).)*?Descriptor: ([a-f0-9]{40})\s*\n(?:(?!^ +\d+:|^Input).)*?ControllerNumber: (\d+)"
    return {int(p): (d, int(i)) for p,d,i in re.findall(pattern, dump, flags=re.S)}

def dolphin(port, cid, wii):
    v = {"Device": f"Android/{cid}/Controller Club P{port}"}
    prefix = "Classic/" if wii else ""
    if wii:
        v["Extension"] = "Classic"
    for k, value in {"A":"Button A", "B":"Button B", "X":"Button X", "Y":"Button Y",
                     ("+" if wii else "Start"):"Start", **({"-":"Select","ZL":"Button L2","ZR":"Button R2"} if wii else {"Z":"Button R1"})}.items():
        v[prefix + "Buttons/" + k] = f"`{value}`"
    for group, x, y in [("Left Stick" if wii else "Main Stick",0,1),
                         ("Right Stick" if wii else "C-Stick",11,14),("D-Pad",15,16)]:
        for direction, axis, sign in [("Up",y,"-"),("Down",y,"+"),("Left",x,"-"),("Right",x,"+")]:
            v[f"{prefix}{group}/{direction}"] = f"`Axis {axis}{sign}`"
    for side, button in [("L","Button L1" if wii else "Button L2"),("R","Button R1" if wii else "Button R2")]:
        v[f"{prefix}Triggers/{side}"] = f"`{button}`"
        v[f"{prefix}Triggers/{side}-Analog"] = f"`{button}`"
    return "[Profile]\n" + "\n".join(f"{k} = {x}" for k,x in v.items()) + "\n"

def cemu(port, descriptor):
    root = ET.Element("emulated_controller")
    ET.SubElement(root,"type").text = "Wii U GamePad" if port==1 else "Wii U Pro Controller"
    ET.SubElement(root,"toggle_display").text="0"
    c = ET.SubElement(root,"controller")
    for k,x in [("api","Android"),("uuid",descriptor),("display_name",f"Controller Club P{port}")]:
        ET.SubElement(c,k).text=x
    for group in ("axis","rotation","trigger"):
        a=ET.SubElement(c,group)
        ET.SubElement(a,"deadzone").text="0.25"
        ET.SubElement(a,"range").text="1"
    # Android native IDs used by Cemu Plus v0.4.1: key codes and Cemu's directional IDs.
    buttons=[96,97,99,100,102,103,104,105,108,109]
    directions=[34,35,36,37,106,107,45,39,44,38,47,41,46,40]
    if port!=1:
        buttons.append(110)
    mapping=ET.SubElement(c,"mappings")
    for i,k in enumerate(buttons+directions,1):
        entry=ET.SubElement(mapping,"entry")
        ET.SubElement(entry,"mapping").text=str(i)
        ET.SubElement(entry,"button").text=str(k)
    ET.indent(root,space="  ")
    return ET.tostring(root,encoding="unicode",xml_declaration=True)+"\n"

def main():
    args=argparse.ArgumentParser(description=__doc__)
    args.add_argument("--install",action="store_true",help="add named profiles to the installed emulators")
    opts=args.parse_args()
    slots=pads()
    if len(slots)!=4:
        args.error("Start routing on the Thor before generating profiles; all four Club pads must be visible.")
    for port,(descriptor,cid) in sorted(slots.items()):
        print(f"Club P{port}: Android controller {cid}")
        if not opts.install:
            continue
        if shell("pm path org.dolphinemu.dolphinemu",check=False).returncode==0:
            install(DOLPHIN+f"Wiimote/Club-P{port}-Wii-Android{cid}.ini",dolphin(port,cid,True))
            install(DOLPHIN+f"GCPad/Club-P{port}-GC-Android{cid}.ini",dolphin(port,cid,False))
        if shell("pm path info.cemu.cemuplus",check=False).returncode==0:
            install(CEMU+f"Club-P{port}.xml",cemu(port,descriptor))

if __name__=="__main__":
    main()

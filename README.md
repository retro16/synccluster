# synccluster

Used to manage machines inside a synccluster. synccluster allows loosely coupled machines to share accounts, data and programs as seamlessly as possible while keeping the script "simple".


# kvm-run

Runs virtual machines with qemu-kvm, optionally with GPU passthrough.
For GPU passthrough, you need to add pci-stub to the graphic card(s) you want to dedicate to virtual machine(s).

 1. Add this line to /etc/initramfs-tools/modules:

    ```pci-stub```


 2. Run this command to update your initramfs:

    ```update-initramfs -u```

 3. Find your vendor/device IDs for the GPU you wish to passthrough:

    ```lspci -nnk```

    For example:

    ```00:02.0 VGA compatible controller [0300]: Intel Corporation Atom Processor Z36xxx/Z37xxx Series Graphics & Display [8086:0f31] (rev 0e)
	Subsystem: Intel Corporation Device [8086:2055]
	Kernel driver in use: i915```

    In that case, the IDs would be _8086:2055_ (although kvm-run does not wotk with forwarded intel GPUs, this is a bad example !)

    If your GPU has an audio adapter (NVIDIA/AMD have one for HDMI output), you will need to forward it.

 4. Modify your grub command-line in /etc/default/grub:

    ```GRUB_CMDLINE_LINUX_DEFAULT="quiet intel_iommu=on pci-stub.ids=1002:6719,1002:aa80"```
    
    Or, for AMD CPUs:

    ```GRUB_CMDLINE_LINUX_DEFAULT="quiet amd_iommu=on pci-stub.ids=1002:6719,1002:aa80"```

 5. Update your grub config file:

    ```update-grub2```

 6. Reboot !

 7. Check that the GPU is handled by the pci-stub module:

    ```lspci -nnk```

    You should see ```Kernel driver in use: pci-stub```

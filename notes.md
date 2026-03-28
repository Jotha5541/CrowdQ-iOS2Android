Setting up Pi3 as a WiFi access point

See link at [Adafruit](https://cdn-learn.adafruit.com/downloads/pdf/setting-up-a-raspberry-pi-as-a-wifi-access-point.pdf) for info on this.  Supposed to be updated frequently

```
    1  sudo apt-get update
    2  sudo apt-get upgrade
    3  sudo apt install -y hostapd dnsmasq
    4  sudo apt-get install resolvconf
    5  sudo systemctl unmask hostapd
    6  sudo systemctl enable hostapd
    8  sudo DEBIAN_FRONTEND=noninteractive apt install -y netfilter-persistent iptables-persistent
    9  sudo reboot
    11  sudo vi /etc/dhcpcd.conf 
   12  sudo vi /etc/dnsmasq.conf 
   13  sudo vi /etc/hostapd/hostapd.conf
   14  sudo raspi-config nonint do_wifi_country US
   15  sudo iptables -t nat -A POSTROUTING -o eth0 -j MASQUERADE
   16  sudo netfilter-persistent save
   17  sudo reboot
```


path="/data/local/tmp/swapfile_test"
sizeMb=100

echo "Creating swap file at $path with size $sizeMb MB..."
dd if=/dev/zero of="$path" bs=1M count=$sizeMb

echo "Setting permissions..."
chmod 600 "$path"

echo "Making swap..."
mkswap "$path"

echo "Enabling swap..."
swapon "$path"

echo "Checking swap..."
free -h
grep Swap /proc/meminfo

echo "Disabling swap..."
swapoff "$path"

echo "Removing swap file..."
rm "$path"


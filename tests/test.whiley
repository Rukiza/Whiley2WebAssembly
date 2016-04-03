function contains(int[] items, int item) -> (bool r):
    int i=0
    while i < |items|:
      if items[i] == item:
        return true
      i = i + 1
    return false
    
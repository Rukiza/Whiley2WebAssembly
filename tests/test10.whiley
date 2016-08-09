method addOne():

    bool[][] ls = [[true, false],[false,true],[false,false], [true, true]]
    assert ls[0][0] == true
    assert ls[0][1] == false
    assert ls[1][0] == false
    assert ls[1][1] == true
    assert ls[2][0] == false
    assert ls[2][1] == false
    assert ls[3][0] == true
    assert ls[3][1] == true
    ls[0][0] = false
    assert ls[0][0] == false

    assert ls[0] != [true,false]
    assert ls[0] == [false, false]
    assert ls[1] == [false, true]
    assert ls[2] == [false, false]
    assert ls[3] == [true, true]

    bool[][] ls2 = ls
    ls2[0][0] = true
    assert ls2[0] == [true,false]
    assert ls[0] == [false, false]

    int[] ls3 = [0;0]
    int[] ls4 = ls3
    {int h, int j}[][] v1 = [[{h: 0, j:10};10];300]
    {int h, int j}[][] v2 = v1


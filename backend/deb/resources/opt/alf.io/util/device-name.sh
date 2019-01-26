#!/bin/bash

# source https://boulderappsco.postach.io/post/convert-decimal-to-base-36-alpha-numeric-in-bash-linux

function decimal_to_base36() {
    BASE36=($(echo {0..9} {A..Z}));
    arg1=$@;
    export BC_LINE_LENGTH=0
    for i in $(bc <<< "obase=36; $arg1"); do
    echo -n ${BASE36[$(( 10#$i ))]}
    done && echo
}

decimal_value=`echo $1 | cksum | sed -n 's/\([A-Z0-9a-z]\+\).*/\1/p'`
decimal_to_base36 ${decimal_value}
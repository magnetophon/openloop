###  Translation from RTF performed by UnRTF, version 0.21.9 
### font table contains 0 fonts total
### invalid font number 0

-----------------

//synchronous looping environment for supercollider
//coded by Eli Fieldsteel
//120-07-11

s = Server.local;
s.reboot;

//allocate buffers
~b1 = Buffer.alloc(s, s.sampleRate*30, 2);
~b2 = Buffer.alloc(s, s.sampleRate*30, 2);
~b3 = Buffer.alloc(s, s.sampleRate*30, 2);
~b4 = Buffer.alloc(s, s.sampleRate*30, 2);
~b5 = Buffer.alloc(s, s.sampleRate*30, 2);
~b6 = Buffer.alloc(s, s.sampleRate*30, 2);
~b7 = Buffer.alloc(s, s.sampleRate*30, 2);
~b8 = Buffer.alloc(s, s.sampleRate*30, 2);

//6 synthdefs:
//input, output, master record, slave record,
//master playback, slave playback


//input synth
SynthDef.new(\input, {
 arg amp=1, recBus=50, dirBus=60;
 var in, recSig, dirSig;

 //convert mono input to stereo via array multichannel expansion
 in = SoundIn.ar([0,0]);
 
 //multiply by amplitude argument & write to bus
 recSig = in*amp;
 dirSig = in*amp;
 Out.ar(recBus, recSig);
 Out.ar(dirBus, dirSig);
}).store;

//master record synth
SynthDef.new(\recM, {
 arg t_start=0, t_stop=0, timerBus=40, inBus=50,
 recLev=1, preLev=0, loop=0, whichBuf;
 var in, isRec, timer;
 
 //record start/stop toggle
 isRec = SetResetFF.kr(t_start, t_stop);
 
 //read audio data from bus
 in = In.ar(inBus, 2);
 
 //seconds between consecutive triggers
 timer = Timer.kr(t_start+t_stop);
 
 //write timer data to bus
 Out.kr(timerBus, timer);
 
 //record buffer
 RecordBuf.ar(in, whichBuf, 0, recLev, preLev, isRec, loop);
}).store;

//slave record synth
SynthDef.new(\recS, {
 arg t_start=0, t_stop=0, inBus=50, 
 recLev=1, preLev=0, loop=0, whichBuf;
 var in, isRec;
 
 //same method as above for starting/stopping recording
 isRec = SetResetFF.kr(t_start, t_stop);
 
 //read audio data from bus
 in = In.ar(inBus, 2);
 
 //record buffer
 RecordBuf.ar(in, whichBuf, 0, recLev, preLev, isRec, loop);
}).store;


//master playback synth
SynthDef.new(\playM, {
 arg rate=1, numBars=12, whichBuf, amp=1, atkThresh=0.02, timerBus=40,
 lengthBus=80, downbeatBus=90, numBarsBus=100, outBus=70;
 var sig, jumpTrig, start, end, length, timer,
 numberOfBars, downbeat, bufStart, phs;
 
 //read timer data from bus
 timer = In.kr(timerBus);
 
 //wrap to length of buffer to avoid overshoot
 timer = timer%(~b1.numFrames/s.sampleRate);
 
 //find index/sample of buffer where sound begins
 //divide by two for 2-channel buffer
 start = IndexInBetween.kr(whichBuf, atkThresh)/2;
 
 //avoid fractional or negative indices
 start = thresh(start.floor, 0);
 
 //end sample (from timer data)
 end = timer*s.sampleRate;
 
 //length (in samples) of useable segment of buffer
 length = end-start;
 
 //impulse generator sends triggers for downbeats
 downbeat = Impulse.kr(numBars*s.sampleRate/length);

 numberOfBars = numBars;
 
 //send a trigger at the beginning of the master loop (downbeat divided by numBars)
 bufStart = PulseDivider.kr(downbeat, numBars, numBars-1);
 
 //audio rate triggerable linear ramp to index into buffer
 //offset by start position
 phs = start+Sweep.ar(bufStart, s.sampleRate);
 
 //use phs to index into buffer, scale by amplitude
 sig = BufRd.ar(2, whichBuf, phs, 1, 2) * amp;
 
 //send various control & audio rate data down the node chain
 Out.kr(lengthBus, length);
 Out.kr(downbeatBus, downbeat);
 Out.kr(numBarsBus, numberOfBars);
 Out.ar(outBus, sig);
}).store;

//slave playback synth
SynthDef.new(\playS, {
 arg rate=1, t_jump=0, lengthMul=1, whichBuf, del=0, atkThresh=0.02,
 amp=1, numBarsBus=100, downbeatBus=90, lengthBus=80, outBus=70;
 var sig, jumpTrig, start, end, mlength, length,
 numBars, downbeat, delbeat, sync, phs;
 
 //read master length data and scale to length of slave loop (user-specified)
 mlength = In.kr(lengthBus);
 length = mlength * lengthMul;
 
 //also read downbeat and number of bars data from busses
 downbeat = In.kr(downbeatBus);
 numBars = In.kr(numBarsBus);
 
 //determine start position in the same way as before
 start = IndexInBetween.kr(whichBuf, atkThresh)/2;
 start = thresh(start.floor, 0);
 
 //no timer data, since the slave is dependent on the master
 //so, instead, end = start + slave length, as derived from the master length
 end = start+length;
 
 //a method for synchronizing:
 //SRFF again - sync acts as a trigger whenever a downbeat is received
 sync = SetResetFF.kr(downbeat, 0);
 
 //sync is used to trigger an envelope which jumps to the start position of the
 //phasor controlling the slave buffer
 jumpTrig = EnvGen.kr(Env.adsr(0.001, 0.001, 0, 1, 1, 0), sync);
 
 //calculate useable data to delay slave loop, in case the loop begins on
 //something other than the downbeat
 //first, calculate the length, in seconds, of a single measure
 //length of master loop, converted to seconds, divided by the number of bars
 delbeat = ((mlength/s.sampleRate)/numBars);
 
 //then, multiply by the delay argument - we'll see how this is used later
 delbeat = delbeat*del;
 
 //phs: audio rate index into buffer
 //initally Silent (audio rate zeros) then triggered by downbeat to jump to Phasor
 //and simultaneously reset Phasor
 phs = Select.ar(sync, [Silent.ar, Phasor.ar(jumpTrig+t_jump, BufRateScale.kr(whichBuf), start, end, start)]);
 
 //use this index to read through buffer
 sig = BufRd.ar(2, whichBuf, phs, 1, 2);

 //apply delay time
 //del=1 means delay by one measure
 //del=0.5 means delay by half a measure, etc.
 sig = DelayN.ar(sig, 2, delbeat)*amp;
 
 //send audio signal out on a bus
 Out.ar(outBus, sig);
}).store;

//output synth
SynthDef.new(\output, {
 arg recBus=70, dirBus=60, recAmp=1, dirAmp=1, mainAmp=1, out=0;
 var recSig, dirSig, outSig;
 
 recSig = In.ar(recBus, 2) * recAmp;
 dirSig = In.ar(dirBus, 2) * dirAmp;
 outSig = (recSig + dirSig) * mainAmp;
 Out.ar(out, outSig);
}).store;

//create groups to ensure proper node chain order
~inGroup = Group.new;
~recGroup = Group.after(~inGroup);
~playMGroup = Group.after(~recGroup);
~playSGroup = Group.after(~playMGroup);
~outGroup = Group.after(~playSGroup);

//a function to be evaluate after terminating signal procesing with cmd-.
//cmd-. frees all groups and synths, so we need to reinstantiate them
//first, zero buffers, then instantiate groups, and instantiate input and output synths

//we can see this in the node tree (boot the server, bring the server into focus, press "p")
~setup = {
 Task {
 ~b1.zero;
 ~b2.zero;
 ~b3.zero;
 ~b4.zero;
 ~b5.zero;
 ~b6.zero;
 ~b7.zero;
 ~b8.zero;
 0.02.wait;
 ~inGroup = Group.new;
 ~recGroup = Group.after(~inGroup);
 ~playMGroup = Group.after(~recGroup);
 ~playSGroup = Group.after(~playMGroup);
 ~outGroup = Group.after(~playSGroup);
 0.02.wait;
 ~in = Synth.new(\input, [], ~inGroup);
 ~out = Synth.new(\output, [], ~outGroup);
 }.start;
};

//remove any previous cmdperiods and register the new one
CmdPeriod.removeAll;
CmdPeriod.add(~setup);

//now hitting cmd-period will run the necessary setup

//run these lines one at a time after above setup has been run
~rec = Synth.new(\recM, [\whichBuf, ~b1], ~recGroup);
~rec.set(\t_start, 1);
~rec.set(\t_stop, 1);~playM = Synth.new(\playM, [\whichBuf, ~b1, \numBars, 12], ~playMGroup);
~recS1 = Synth.new(\recS, [\whichBuf, ~b2], ~recGroup);
~recS1.set(\t_start, 1);
~recS1.set(\t_stop, 1);~playS1 = Synth.new(\playS, [\whichBuf, ~b2, \lengthMul, 1/3, \del, 0.5/3], ~playSGroup);

//free slave loop
~recS1.free;~playS1.free;

//free master loop
~recM.free;~playM.free;


//cmd period won't clean up anymore, so we can just free everything directly when finished
s.freeAll;
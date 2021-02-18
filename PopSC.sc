PopChannel {
	var <server, <bus, <group, <synthGroup, <fxGroup, <sendGroup;

	*addSynthDefs {
		SynthDef(\popSend, {
			var snd;
			snd = In.ar(\in.kr(0), 2);
			Out.ar(\out.kr(0), snd);
		}).add;
	}

	*new {
		^super.new.init;
	}

	init {
		server = Server.default;
		bus = Bus.audio(server, 2);
		group = Group();
		synthGroup = Group(synthGroup);
		fxGroup = Group(fxGroup, \addToTail);
		sendGroup = Group(sendGroup, \addToTail);
	}

	synth { |def, args|
		^Synth(def, args ++ [out: bus], synthGroup);
	}

	fx { |def, args|
		^Synth(def, args ++ [out: bus], fxGroup);
	}

	send { |outBus|
		^Synth(\popSend, [in: bus, out: outBus], sendGroup);
	}
}

PopSong {
	var <root, <scale;
	var <bpm, <timeSignature, <subdivisions;

	*new { |bpm, timeSignature, subdivisions, root, scale|
		^super.new.init(bpm, timeSignature, subdivisions, root, scale);
	}

	init { |argBpm, argTimeSignature, argSubdivisions, argRoot, argScale|
		bpm = argBpm;
		timeSignature = argTimeSignature;
		subdivisions = argSubdivisions;
		root = argRoot;
		scale = argScale ?? { [0, 2, 4, 5, 7, 9, 11] };
	}

	beat {
		^60 / bpm;
	}

	tatum {
		^this.beat / subdivisions;
	}

	bar {
		^this.beat * timeSignature;
	}

	degreeToFreq { |degree, octave|
		^(60 + root + scale.wrapAt(degree) + ((degree.div(scale.size) + octave) * 12)).midicps
	}
}

PopDrumLoop {
	var channel;
	var pattern;
	var synthDef;

	*new { |synthDef, pattern|
		^super.new.init(pattern, synthDef);
	}

	init { |argPattern, argSynthDef|
		pattern = argPattern;
		if(pattern.isKindOf(String)) {
			pattern = pattern.collectAs({ |character|
				case
				{ "0123456789abcdef".includes(character) } {
					"0123456789abcdef".indexOf(character) / 15
				}
				{ character == $. } {
					0
				}
				{ character == $x } {
					1
				}
				{ "\n\t ".includes(character) } {
					\ignore
				};
			}, Array).select { |x| x != \ignore };
		};
		synthDef = argSynthDef;
	}

	play { |song, channel|
		loop {
			pattern.do { |probability|
				if (probability.coin) {
					Server.default.makeBundle(Server.default.latency, {
						channel.synth(synthDef);
					});
				};
				song.tatum.wait;
			};
		};
	}
}

PopMelody {
	var pattern;
	var synthDef;
	var octave;
	var hasGate;

	*new { |synthDef, pattern, octave, hasGate = false|
		^super.new.init(pattern, synthDef, octave, hasGate);
	}

	init { |argPattern, argSynthDef, argOctave, argHasGate|
		pattern = argPattern;
		octave = argOctave;
		hasGate = argHasGate;
		if(pattern.isKindOf(String)) {
			pattern = pattern.collectAs({ |character|
				case
				{ "0123456789abcdef".includes(character) } {
					"0123456789abcdef".indexOf(character)
				}
				{ character == $. } {
					nil
				}
				{ character == $x } {
					1
				}
				{ character == $- } {
					\sustain
				}
				{ "\n\t ".includes(character) } {
					\ignore
				};
			}, Array).select { |x| x != \ignore };
		};
		synthDef = argSynthDef;
	}

	play { |song, channel|
		var synth;
		loop {
			pattern.do { |degree|
				if (degree.isNil) {
					if (hasGate and: { synth.notNil }) {
						Server.default.makeBundle(Server.default.latency, {
							synth.set(\gate, 0);
							synth = nil;
						});
					};
				} {
					if (degree != \sustain) {
						Server.default.makeBundle(Server.default.latency, {
							if (hasGate and: { synth.notNil }) {
								synth.set(\gate, 0);
								synth = nil;
							};
							synth = channel.synth(synthDef, [freq: song.degreeToFreq(degree, octave)]);
						});
					};
				};
				song.tatum.wait;
			};
		};
	}
}

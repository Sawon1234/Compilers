parser grammar CubexParser;
options { tokenVocab = CubexLexer; }

vname returns [CubexVName cn]
	: NAME { $cn =  new CubexVName($NAME.text); };

cname returns [CubexCName cn]
	: CLASSNAME { $cn =  new CubexCName($CLASSNAME.text); };

pname returns [CubexPName cn]
	: TYPEPARAM { $cn =  new CubexPName($TYPEPARAM.text); };


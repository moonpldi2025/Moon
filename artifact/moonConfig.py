time_out = 5400

zipper = "ZIPPER"
debloaterx = "DEBLOATERX"
conch = "CONCH"
plain = "PLAIN"
_2o = "2o"
_3o = "3o"
oot = "Oot"
oom = "Oom"


unscalable = [
    ("eclipse", plain, _2o, oom),
    ("eclipse", plain, _3o, oom),
    ("eclipse", debloaterx, _3o, oom),
    ("eclipse", zipper, _2o, oom),
    ("eclipse", zipper, _3o, oot),
    ("eclipse", conch, _3o, oom),
    ("findbugs", plain, _3o, oom),
    ("findbugs", zipper, _3o, oot),
    ("checkstyle", plain, _2o, oom),
    ("checkstyle", plain, _3o, oom),
    ("checkstyle", zipper, _3o, oot),
    ("checkstyle", conch, _3o, oot),
    ("chart", plain, _3o, oom),
    ("chart", conch, _3o, oot),
    ("batik", plain, _3o, oom),
    ("batik", zipper, _3o, oot),
    ("batik", conch, _3o, oom),
    ("batik", debloaterx, _3o, oom),
    ("xalan", plain, _3o, oom),
    ("bloat", plain, _3o, oot),
    ("dcevm", plain, _3o, oom),
    ("dcevm", zipper, _3o, oom),
    ("dcevm", conch, _3o, oot),
    ("recaf", plain, _3o, oom),
    ("recaf", zipper, _3o, oom),
    ("recaf", conch, _3o, oom),
    ("bytecodeviewer", plain, _3o, oot),
    ("bytecodeviewer", zipper, _3o, oot),
    ("bytecodeviewer", conch, _3o, oot),
    ("bytecodeviewer", debloaterx, _3o, oot),
    ("jd", plain, _3o, oom),
    ("sunflow", plain, _3o, oom),
]


def isUnscalable(app, algo, sens):
    for item in unscalable:
        if item[0] == app and item[1] == algo and item[2] == sens:
            return True
    return False
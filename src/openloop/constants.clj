(ns openloop.core )

;; set some constants
(def max-loop-seconds 30)
(def nr-chan 2)
(def nr-loops 8)

;; max loop length is just a few samples while designing the data structures
;; (def max-loop-length 8)
(def max-loop-length (* 44100 max-loop-seconds))
;; (s/exercise ::loop-audio-type 2)
(def max-undo-nr 16)
(def max-sources-nr 128)
(def max-x-fade-length 1024 )

(ns openloop.core )

;; set some constants
(def max-loop-seconds 30)
(def nr-chan 2)
(def nr-loops 8)

;; max loop length is just a few samples while designing the data structures
;; (def max-loop-length 8)
;; for now we fix the SR that spec uses, cause to get the real SR we need a booted server
;; from the help of buf-rd:
;; The phase argument only offers precision for addressing 2**24 samples (about 6.3 minutes at 44100Hz).
(def max-loop-length
  (let [wanted-length (* 44100 max-loop-seconds) ]
    (min wanted-length 16777216)
  )


  )
;; (s/exercise ::loop-audio-type 2)
(def max-undo-nr 16)
(def max-sources-nr 128)
(def max-x-fade-length 1024 )
(def default-x-fade-length 10)
;; the maximum value of the recording phasor
;; a phasor cannot get a higher value than this:  2^31
;; it translates to more than 13 hours, so should be fine.
(def max-phasor-val 2147483648)
;; (def mode-bus 1007)
(def loop-nr-bus 1008)
(def mode-bus-base 500)

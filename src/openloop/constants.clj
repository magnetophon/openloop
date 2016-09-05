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
;; (def max-phasor-val 2147483648)
;; (def mode-bus 1007)
;;***********************************************************************************************
;; bus numbers
;;***********************************************************************************************
(defonce in-bus 50)
(defonce dir-bus 60)
(defonce rec-bus 70)
;; (defonce loop-nr-bus 1008)
(defonce rec-clock-bus 42)
(defonce short-length-bus 43)
(defonce short-clock-bus 44)
(defonce bus-base 500)
(defonce mode-bus-base bus-base)
(defonce now-bus-base (+ nr-loops mode-bus-base))

;; (defonce dry-bus (audio-bus nr-chan))
;; (defonce out-bus (audio-bus nr-chan))
(defonce short-clock-bus (audio-bus))
(defonce long-clock-bus (audio-bus))

(defproject thornydev/go-lightly "0.3-SNAPSHOT"
  :description "Facilitating Go concurrency features in Clojure"
  :url "https://www.youtube.com/watch?v=f6kdp27TYZs&feature=youtu.be"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :profiles {:dev {:dependencies [[criterium "0.3.1"]]}}
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [lamina "0.5.0-beta8"]
                 [enlive "1.0.1"]]
  :source-paths ["src" "clj-examples"]
  :javac-options ["-target" "1.7"]
  :jvm-opts ["-Xmx2g" "-server"] 
  :main thornydev.go-lightly.examples.run-examples)

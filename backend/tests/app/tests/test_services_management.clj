;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; This Source Code Form is "Incompatible With Secondary Licenses", as
;; defined by the Mozilla Public License, v. 2.0.
;;
;; Copyright (c) 2021 UXBOX Labs SL

(ns app.tests.test-services-management
  (:require
   [app.common.uuid :as uuid]
   [app.db :as db]
   [app.http :as http]
   [app.storage :as sto]
   [app.tests.helpers :as th]
   [clojure.test :as t]
   [buddy.core.bytes :as b]
   [datoteka.core :as fs]))

(t/use-fixtures :once th/state-init)
(t/use-fixtures :each th/database-reset)

(t/deftest duplicate-file
  (let [profile (th/create-profile* 1 {:is-active true})
        project (th/create-project* 1 {:team-id (:default-team-id profile)
                                       :profile-id (:id profile)})
        file1   (th/create-file* 1 {:profile-id (:id profile)
                                    :project-id (:id project)})
        file2   (th/create-file* 2 {:profile-id (:id profile)
                                    :project-id (:id project)
                                    :is-shared true})

        libl    (th/link-file-to-library* {:file-id (:id file1)
                                           :library-id (:id file2)})

        data    {::th/type :duplicate-file
                 :profile-id (:id profile)
                 :file-id (:id file1)}
        out     (th/mutation! data)]

    ;; Check tha tresult is correct
    (t/is (nil? (:error out)))
    (let [result (:result out)]
      (t/is (= (:name file1) (:name result)))
      (t/is (not= (:id file1) (:id result)))

      ;; Check that the new file has a correct file library relation
      (let [[item :as rows] (db/query th/*pool* :file-library-rel {:file-id (:id result)})]
        (t/is (= 1 (count rows)))
        (t/is (= (:id file2) (:library-file-id item))))

      ;; Check the total number of files
      (let [rows (db/query th/*pool* :file {:project-id (:id project)})]
        (t/is (= 3 (count rows))))

      )))


(t/deftest duplicate-project
  (let [profile (th/create-profile* 1 {:is-active true})
        project (th/create-project* 1 {:team-id (:default-team-id profile)
                                       :profile-id (:id profile)})
        file1   (th/create-file* 1 {:profile-id (:id profile)
                                    :project-id (:id project)})
        file2   (th/create-file* 2 {:profile-id (:id profile)
                                    :project-id (:id project)
                                    :is-shared true})

        libl    (th/link-file-to-library* {:file-id (:id file1)
                                           :library-id (:id file2)})

        data    {::th/type :duplicate-project
                 :profile-id (:id profile)
                 :project-id (:id project)}
        out     (th/mutation! data)]

    ;; Check tha tresult is correct
    (t/is (nil? (:error out)))
    (let [result (:result out)]
      (t/is (= (:name project) (:name result)))
      (t/is (not= (:id project) (:id result)))

      ;; Check the total number of projects
      (let [rows (db/query th/*pool* :project
                           {:team-id (:default-team-id profile)})]
        (t/is (= 3 (count rows))))

      ;; Check that the new project has the same files
      (let [p1-files (db/query th/*pool* :file
                               {:project-id (:id project)}
                               {:order-by [:name]})
            p2-files (db/query th/*pool* :file
                               {:project-id (:id result)}
                               {:order-by [:name]})]
        (t/is (= (count p1-files)
                 (count p2-files)))

        (doseq [[file1 file2] (map vector p1-files p2-files)]
          (t/is (= (:name file1) (:name file2)))
          (t/is (b/equals? (:data file1)
                           (:data file2))))

        ))))





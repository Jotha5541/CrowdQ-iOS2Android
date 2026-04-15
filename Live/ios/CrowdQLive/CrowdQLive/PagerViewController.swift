//
//  PagerViewController.swift
//  CrowdQ Live
//
//  Created by Patrick Miller on 1/26/26.
//


//
//  Pager.swift
//  CrowdQ Stage
//
//  Created by Patrick Miller on 1/18/26.
//

import UIKit

final class PagerViewController: UIPageViewController, UIPageViewControllerDataSource, UIPageViewControllerDelegate {

    // Should be public so we can set our own
    var pages: [UIViewController] = [
    ]

    override func viewDidLoad() {
        super.viewDidLoad()

        dataSource = self
        delegate = self

        setViewControllers(
            [pages[0]],
            direction: .forward,
            animated: false
        )
    }
}


// This handles the left/right swipe
extension PagerViewController {

    func pageViewController(
        _ pageViewController: UIPageViewController,
        viewControllerBefore viewController: UIViewController
    ) -> UIViewController? {

        guard
            let index = pages.firstIndex(of: viewController),
            index > 0
        else { return nil }

        return pages[index - 1]
    }

    func pageViewController(
        _ pageViewController: UIPageViewController,
        viewControllerAfter viewController: UIViewController
    ) -> UIViewController? {

        guard
            let index = pages.firstIndex(of: viewController),
            index < pages.count - 1
        else { return nil }

        return pages[index + 1]
    }
}

// Pager.swift

package stage;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import java.util.ArrayList;
import java.util.List;

// Equivalent to UIPageViewController
public class PagerAdapter extends FragmentStateAdapter {

    private final List<Fragment> pages = new ArrayList<>();

    public PagerAdapter(@NonNull FragmentActivity activity) {
        super(activity);
    }

    public void addPage(Fragment fragment) {
        pages.add(fragment);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        return pages.get(position);
    }

    @Override
    public int getItemCount() {
        return pages.size();
    }
}